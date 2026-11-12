package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.*;
import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.araka.ArakaResult;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import cd.gouv.finances.signalements.report.prime.PrimeGuard;
import cd.gouv.finances.signalements.report.validation.RewardPaymentGuard;
import java.net.URI;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@EnabledIfEnvironmentVariable(named="VALIDATION_JDBC_URL", matches=".+")
class PaymentTransfersMigrationTest {
    @Test void preservesExistingRowsAndSerializesClaimsRecoveryAndFinancialConfirmation() throws Exception {
        var base=System.getenv("VALIDATION_JDBC_URL");var user=System.getenv("VALIDATION_DB_USER");var password=System.getenv("VALIDATION_DB_PASSWORD");
        var db="araka_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,user,password);var s=c.createStatement()){s.execute("create database "+db);}
        var url=base.substring(0,base.lastIndexOf('/')+1)+db;
        var ds=new DriverManagerDataSource(url,user,password);var jdbc=new JdbcTemplate(ds);
        Flyway.configure().dataSource(url,user,password).target("45").load().migrate();
        var report=UUID.randomUUID();var prime=UUID.randomUUID();var statement=UUID.randomUUID();var line=UUID.randomUUID();var actor=UUID.randomUUID();
        seed(jdbc,report,prime,statement,line,actor);
        var migration=Flyway.configure().dataSource(url,user,password).load();migration.migrate();migration.validate();
        assertThat(jdbc.queryForObject("select count(*) from app.payment_attempt where reference_code like 'SIM-%'",Integer.class)).isEqualTo(1);
        assertThat(new PrimeGuard(jdbc).paid(prime)).isFalse();
        var registry=new BeneficiaryRegistry(jdbc,null,null,Clock.systemUTC());
        var sandbox=store(jdbc,registry,new ArakaProperties(URI.create("https://"+ArakaProperties.SANDBOX_HOST),"fixture","fixture",null,false));
        var request=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var results=pool.<PaymentTransfers.Claim>invokeAll(List.of(
                    ()->sandbox.reserveTest("0810000001","mpesa",request,actor),
                    ()->sandbox.reserveTest("+243810000001","mpesa",request,actor)));
            assertThat(results.stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).filter(PaymentTransfers.Claim::submit)).hasSize(1);
        }
        var test=sandbox.recent().getFirst();
        assertThat(test.reference()).hasSize(20);
        sandbox.submitted(test.id(),new ArakaResult("paid","approved","T-SANDBOX",false));
        assertThat(new PrimeGuard(jdbc).paid(prime)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from app.payment_attempt",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->sandbox.reserveTest("0827243119","mpesa",UUID.randomUUID(),actor)).isInstanceOf(ReportRejected.class);

        var production=store(jdbc,registry,new ArakaProperties(URI.create("https://fixture.example.invalid"),"fixture","fixture",ArakaProperties.Mode.PRODUCTION,true));
        var claim=production.reserveLine(statement,line,"mpesa",UUID.randomUUID(),actor);
        assertThat(claim.submit()).isTrue();
        assertThatThrownBy(()->production.reserveLine(statement,line,"mpesa",UUID.randomUUID(),actor)).isInstanceOf(ReportRejected.class);
        assertThatThrownBy(()->PaymentHold.requireNoPending(jdbc,report)).isInstanceOf(ReportRejected.class);
        production.submitted(claim.transfer().id(),ArakaResult.unknown("network-uncertain"));
        var old=production.claimCheck(claim.transfer().id());
        assertThat(old.lease()).isNotNull();
        assertThat(production.claimCheck(claim.transfer().id()).lease()).isNull();
        jdbc.update("update app.payment_transfer set lease_until=now()-interval '1 second' where id=?",claim.transfer().id());
        var current=production.claimCheck(claim.transfer().id());
        production.checked(old,new ArakaResult("paid","approved","STALE",false));
        assertThat(production.read(claim.transfer().id()).state()).isEqualTo("unknown");
        var approved=new ArakaResult("paid","approved","T-LIVE-FIXTURE",false);
        production.checked(current,approved);production.checked(current,approved);
        assertThat(new PrimeGuard(jdbc).paid(prime)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from app.payment_attempt where reference_code not like 'SIM-%'",Integer.class)).isEqualTo(1);
        PaymentHold.requireNoPending(jdbc,report);

        var interrupted=sandbox.reserveTest("0840000001","orange",UUID.randomUUID(),actor).transfer();
        jdbc.update("update app.payment_transfer set started_at=now()-interval '2 minutes' where id=?",interrupted.id());
        var recovery=sandbox.claimCheck(interrupted.id());
        assertThat(recovery.lease()).isNotNull();
        sandbox.checked(recovery,new ArakaResult("failed","declined","T-DECLINED",false));
        sandbox.submitted(interrupted.id(),approved);
        assertThat(sandbox.read(interrupted.id()).state()).isEqualTo("failed");
    }

    private static PaymentTransfers store(JdbcTemplate jdbc,BeneficiaryRegistry registry,ArakaProperties properties) {
        var target=new PaymentTransfers(jdbc,registry,new RewardPaymentGuard(jdbc,registry),new PrimeGuard(jdbc),properties,Clock.systemUTC());
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        var advice=new TransactionInterceptor();advice.setTransactionManager(new JdbcTransactionManager(jdbc.getDataSource()));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());proxy.addAdvice(advice);
        return (PaymentTransfers)proxy.getProxy();
    }

    private static void seed(JdbcTemplate j,UUID report,UUID prime,UUID statement,UUID line,UUID actor) {
        j.update("insert into app.report(id,external_id,source,reference_code,reported_on,reported_at,title,description,reporter_phone,imported_at,validation_state) values (?,'ARAKA','test','ARAKA',current_date,now(),'Recette','Synthétique','0810000001',now(),'validated')",report);
        j.update("insert into app.prime(id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at,report_id,reward_setting_id) values (?,'araka-person','0810000001','fixed-reward',0,5000,'approved',?,now(),now(),?,'50002026-1112-4000-8000-000000000001')",prime,actor,report);
        j.update("insert into app.payout_number values (?,'araka-person','0810000001','ready',?,now(),now())",UUID.randomUUID(),actor);
        j.update("insert into app.payment_statement(id,reference_code,status,prepared_by,prepared_at,controlled_at,visaed_at,authorized_at,authorized_by,due_on) values (?,'EP-TEST','authorized',?,now(),now(),now(),now(),?,current_date)",statement,actor,UUID.randomUUID());
        j.update("insert into app.payment_statement_line values (?,?,?,0,5000,'0810000001',false)",line,statement,prime);
        j.update("insert into app.payment_attempt(id,statement_id,line_id,operator,result,reference_code,actor_id,recorded_at) values (?,?,?,'m-pesa','paid','SIM-MPESA-000001',?,now())",UUID.randomUUID(),statement,line,actor);
    }
}

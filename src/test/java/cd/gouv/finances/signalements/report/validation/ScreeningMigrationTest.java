package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import cd.gouv.finances.signalements.report.duplicate.AutomaticDuplicates;
import cd.gouv.finances.signalements.report.duplicate.DuplicateLookup;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named="VALIDATION_JDBC_URL", matches=".+")
class ScreeningMigrationTest {
    @Test void reclassifiesStoredReadingsAndKeepsOneDossierPerIdenticalAttachmentSet() throws Exception {
        var base = System.getenv("VALIDATION_JDBC_URL");
        var user = System.getenv("VALIDATION_DB_USER");
        var password = System.getenv("VALIDATION_DB_PASSWORD");
        var name = "screening_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base,user,password); var sql=connection.createStatement()) {
            sql.execute("create database " + name);
        }
        var url = base.substring(0,base.lastIndexOf('/')+1)+name;
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        Flyway.configure().dataSource(url,user,password).target("46").load().migrate();
        var missing = report(jdbc,"MISSING",null,null,"READABLE","missing");
        var nameOnly = report(jdbc,"NAME-ONLY","Vendeur",null,"READABLE","name-only");
        var nifOnly = report(jdbc,"NIF-ONLY",null,"TEST-NIF","READABLE","nif");
        var failed = report(jdbc,"FAILED",null,null,null,"failed");
        var unreadable = report(jdbc,"UNREADABLE",null,null,"UNREADABLE","unreadable");
        var first = report(jdbc,"FIRST","Vendeur","TEST-NIF","READABLE","shared");
        var second = report(jdbc,"SECOND","Vendeur","TEST-NIF","READABLE","shared");
        var third = report(jdbc,"THIRD","Vendeur","TEST-NIF","READABLE","shared");
        jdbc.update("update app.report set reported_at=reported_at-interval '1 day' where id=?",first);
        var flyway = Flyway.configure().dataSource(url,user,password).load();
        flyway.migrate(); flyway.validate();
        assertThat(jdbc.queryForObject("select validation_state from app.report where id=?",String.class,first)).isEqualTo("queued");

        var rewards=mock(FixedRewards.class);
        var evaluation=new AssessmentEvaluation(new AssessmentEvidence(jdbc),rewards);
        var store=new AssessmentStore(jdbc,new ObjectMapper(),evaluation);
        var decision=new AssessmentDecision(jdbc,store,evaluation,rewards,Clock.systemUTC());
        for(var id:java.util.List.of(missing,nameOnly,nifOnly,failed,unreadable)) decision.apply(id,"automatic",null);
        assertThat(reason(jdbc,missing)).isEqualTo("identity-missing");
        assertThat(store.view(missing).precalculation()).isNotNull();
        assertThat(reason(jdbc,nameOnly)).isEqualTo("identity-missing");
        assertThat(store.read(nifOnly).state()).isEqualTo("doubt");
        assertThat(reason(jdbc,nifOnly)).isNull();
        assertThat(store.read(failed).state()).isEqualTo("doubt");
        assertThat(reason(jdbc,failed)).isNull();
        assertThat(reason(jdbc,unreadable)).isEqualTo("unreadable");

        var priorities=new PrepareDuplicateAlerts(null,null,new DuplicateLookup(jdbc),null,Clock.systemUTC());
        var duplicates=new AutomaticDuplicates(jdbc,priorities,decision,new AnalysisReplay(""),rewards);
        duplicates.execute(third);
        duplicates.prepareUniqueness(third); decision.apply(third,"automatic",null);
        assertThat(store.read(first).state()).isEqualTo("doubt");
        assertThat(store.read(first).uniqueness()).isEqualTo("unique");
        assertThat(reason(jdbc,second)).isEqualTo("duplicate");
        assertThat(reason(jdbc,third)).isEqualTo("duplicate");
        assertThat(jdbc.queryForObject("select count(*) from app.report where id in (?,?,?) and validation_state <> 'rejected'",Integer.class,first,second,third)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app.report_photo",Integer.class)).isEqualTo(8);
        var eventCount=jdbc.queryForObject("select count(*) from app.assessment_event",Integer.class);
        duplicates.execute(third);
        assertThat(jdbc.queryForObject("select count(*) from app.assessment_event",Integer.class)).isEqualTo(eventCount);

        // One shared attachment in a larger set does not prove the reports are duplicates.
        var multiple=report(jdbc,"MULTIPLE","Vendeur","TEST-NIF","READABLE","shared");
        photo(jdbc,multiple,"extra",null);
        assertThat(new AssessmentEvidence(jdbc).selected(multiple,null)).isEqualTo(store.read(multiple).photoId());
        duplicates.execute(multiple);
        decision.apply(multiple,"automatic",null);
        assertThat(reason(jdbc,multiple)).isNull();
        assertThat(store.read(multiple).state()).isEqualTo("doubt");

        // Different photographs of one fully identified invoice are also duplicates.
        var invoiceA=report(jdbc,"INVOICE-A","Vendeur","TEST-NIF","READABLE","invoice-a");
        var invoiceB=report(jdbc,"INVOICE-B","Vendeur","TEST-NIF","READABLE","invoice-b");
        var otherDate=report(jdbc,"OTHER-DATE","Vendeur","TEST-NIF","READABLE","other-date");
        for(var id:java.util.List.of(invoiceA,invoiceB,otherDate)) {
            jdbc.update("""
                    update app.report_assessment set document_number='FACT-1', extracted='{"sellerTaxId":"TEST-NIF","documentNumber":"FACT-1",
                    "documentDate":"2026-11-10","totalAmount":"116000","currency":"CDF"}'::jsonb where report_id=?
                    """,id);
        }
        jdbc.update("update app.report set reported_at=reported_at-interval '1 day' where id=?",invoiceA);
        jdbc.update("update app.report_assessment set document_date='2026-11-11', extracted=jsonb_set(extracted,'{documentDate}','\"2026-11-11\"') where report_id=?",otherDate);
        duplicates.execute(invoiceB); decision.apply(invoiceB,"automatic",null);
        assertThat(reason(jdbc,invoiceB)).isEqualTo("duplicate");
        duplicates.execute(otherDate); decision.apply(otherDate,"automatic",null);
        assertThat(reason(jdbc,otherDate)).isNull();

        assertThat(jdbc.queryForObject("select count(*) from app.assessment_event where report_id=? and rule_version=?",Integer.class,missing,ValidationRules.VERSION)).isEqualTo(1);

        var review=new ReviewAssessment(jdbc,store,new AssessmentEvidence(jdbc),decision,
                mock(cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry.class),Clock.systemUTC(),duplicates);
        review.fiscalStatus(nifOnly,new FiscalStatusReview(store.read(nifOnly).revision(),"unregistered","Référentiel fiscal de recette"),UUID.randomUUID());
        assertThat(store.read(nifOnly).vatStatus()).isEqualTo("unregistered");
        assertThat(store.read(nifOnly).vatEvidence()).contains("Référentiel fiscal");

        jdbc.update("update app.report_assessment set vat_charged='yes',vat_charge_evidence='TVA extraite automatiquement' where report_id=?",nifOnly);
        var before=store.read(nifOnly);
        review.execute(nifOnly,new AssessmentReview(before.revision(),"Vendeur corrigé","TEST-NIF","FACTURE-CORRIGÉE","2026-11-10","non-normalized"),UUID.randomUUID());
        var corrected=store.read(nifOnly);
        assertThat(corrected.photoId()).isEqualTo(before.photoId());
        assertThat(corrected.vatStatus()).isEqualTo(before.vatStatus());
        assertThat(corrected.vatEvidence()).isEqualTo(before.vatEvidence());
        assertThat(corrected.vatCharged()).isEqualTo("yes");
        assertThat(corrected.vatChargeEvidence()).isEqualTo("TVA extraite automatiquement");
        assertThat(corrected.uniqueness()).isEqualTo("unique");
        assertThat(corrected.extracted()).isEqualTo(before.extracted());
        assertThat(corrected.state()).isEqualTo("validated");
        review.execute(nifOnly,new AssessmentReview(corrected.revision(),"Vendeur corrigé","NIF-CORRIGÉ","FACTURE-CORRIGÉE","2026-11-10","non-normalized"),UUID.randomUUID());
        assertThat(store.read(nifOnly).vatStatus()).isEqualTo("unknown");
        assertThat(store.read(nifOnly).state()).isEqualTo("doubt");
        review.execute(nameOnly,new AssessmentReview(store.read(nameOnly).revision(),"Vendeur","NIF-AJOUTÉ","FACTURE","2026-11-10","non-normalized"),UUID.randomUUID());
        assertThat(store.read(nameOnly).sellerTaxId()).isEqualTo("NIF-AJOUTÉ");
        assertThat(reason(jdbc,nameOnly)).isNull();
    }

    private static String reason(JdbcTemplate jdbc,UUID id) {
        return jdbc.queryForObject("select screening_reason from app.report where id=?",String.class,id);
    }

    private static UUID report(JdbcTemplate jdbc,String reference,String seller,String nif,String quality,String fingerprint) {
        var id=UUID.randomUUID();
        jdbc.update("""
                insert into app.report(id,external_id,source,reference_code,reported_on,reported_at,title,description,imported_at)
                values(?,?,'test',?,current_date,current_timestamp,'Recette','Données synthétiques',now())
                """,id,reference,reference);
        var photo=photo(jdbc,id,fingerprint,quality);
        jdbc.update("""
                insert into app.report_assessment(report_id,job_state,photo_id,seller_name,seller_tax_id,document_number,document_date)
                values(?,'done',?,?,?,'TEST-FACTURE','2026-11-10')
                """,id,photo,seller,nif);
        if(nif!=null) {
            var reading=jdbc.queryForObject("select id from app.document_reading where document_reference=?",UUID.class,photo.toString());
            jdbc.update("insert into app.extracted_field(id,reading_id,name,value,present) values(?,?,'sellerTaxId',?,true)",UUID.randomUUID(),reading,nif);
        }
        return id;
    }

    private static UUID photo(JdbcTemplate jdbc,UUID report,String fingerprint,String quality) {
        var id=UUID.randomUUID();
        jdbc.update("insert into app.report_photo(id,report_id,external_id,content_type,content,fingerprint) values(?,?,?,'image/png',?,?)",
                id,report,id.toString(),new byte[]{1,2,3},fingerprint);
        jdbc.update("""
                insert into app.document_reading(id,document_reference,fingerprint,status,quality,schema_version,created_at)
                values(?,?,?, ?,?,'test',now())
                """,UUID.randomUUID(),id.toString(),fingerprint,quality==null?"FAILED":"READ",quality);
        return id;
    }
}

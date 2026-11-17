package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.access.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithRoleContextFactory.class)
public @interface WithRole { Role value(); }

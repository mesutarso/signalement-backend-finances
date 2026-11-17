package cd.gouv.finances.signalements.access.controller;

import cd.gouv.finances.signalements.access.Permission;
import cd.gouv.finances.signalements.access.Role;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RbacController {
    public record RoleView(String code, String label, String description, List<String> permissions) { }
    public record PermissionView(String code, String group, String label) { }
    public record Catalogue(List<RoleView> roles, List<PermissionView> permissions) { }

    @GetMapping("/api/access/roles")
    @PreAuthorize("hasAuthority('rbac.read')")
    public Catalogue roles() {
        return new Catalogue(Arrays.stream(Role.values()).map(r -> new RoleView(r.name(), r.label(), r.description(), Permission.forRole(r))).toList(),
                Arrays.stream(Permission.values()).map(p -> new PermissionView(p.code(), p.group(), p.label())).toList());
    }
}

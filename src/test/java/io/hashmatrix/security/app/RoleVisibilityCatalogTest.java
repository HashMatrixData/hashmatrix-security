package io.hashmatrix.security.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.hashmatrix.security.domain.AssetRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 「角色→可见库表」映射单测：守护库表级角色门的可见性语义——
 * 已知角色返回预期库表、未知/空角色 fail-closed（空集）、多角色取并集。
 */
class RoleVisibilityCatalogTest {

    private final RoleVisibilityCatalog catalog = new RoleVisibilityCatalog();

    @Test
    void known_role_sees_expected_tenant_tables() {
        Set<AssetRef> visible = catalog.visibleAssets(List.of("security-viewer"));

        assertThat(visible)
                .extracting(AssetRef::qualifiedName)
                .containsExactlyInAnyOrder("sec_*.classification", "sec_*.label");
    }

    @Test
    void unknown_role_sees_nothing_fail_closed() {
        assertThat(catalog.visibleAssets(List.of("some-other-role"))).isEmpty();
    }

    @Test
    void null_or_empty_roles_see_nothing() {
        assertThat(catalog.visibleAssets(null)).isEmpty();
        assertThat(catalog.visibleAssets(List.of())).isEmpty();
    }

    @Test
    void multiple_roles_union_their_visible_tables() {
        RoleVisibilityCatalog custom =
                new RoleVisibilityCatalog(
                        Map.of(
                                "viewer-a", Set.of(AssetRef.tenantTable("classification")),
                                "viewer-b", Set.of(AssetRef.tenantTable("label"))));

        Set<AssetRef> visible = custom.visibleAssets(List.of("viewer-a", "viewer-b"));

        assertThat(visible)
                .extracting(AssetRef::qualifiedName)
                .containsExactlyInAnyOrder("sec_*.classification", "sec_*.label");
    }

    @Test
    void unknown_role_does_not_dilute_known_role() {
        Set<AssetRef> visible = catalog.visibleAssets(List.of("security-viewer", "some-other-role"));

        assertThat(visible)
                .extracting(AssetRef::qualifiedName)
                .containsExactlyInAnyOrder("sec_*.classification", "sec_*.label");
    }
}

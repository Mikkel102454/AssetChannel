package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.entity.PlayerPostEffects;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class ClientEffectLeasesTest {
    @Test
    void compatibleChannelsShareOneRendererCullNeutralAndRestoreIndependentEffects() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var effects = mock(PlayerPostEffects.class);
            when(player.postEffects()).thenReturn(effects);
            var external = Key.key("other:effect");
            var values = new AtomicReference<List<Key>>(List.of(external));
            when(effects.values()).thenAnswer(i -> values.get());
            when(effects.set(any()))
                    .thenAnswer(i -> {
                        values.set(List.copyOf(i.getArgument(0)));
                        return true;
                    });
            var zoomAsset = composed("zoom", EffectCurveAsset.Kind.ZOOM);
            var fadeAsset = composed("fade", EffectCurveAsset.Kind.FADE);
            var manager = new ClientEffectLeases();
            var zoom = manager.acquire(player, zoomAsset);
            var fade = manager.acquire(player, fadeAsset);
            var moving =
                    new ClientEffectFrame(
                            new EffectAnimation(1, 2, 0, 40, EffectAnimation.Easing.SMOOTH),
                            10,
                            10,
                            0);
            var neutral =
                    new ClientEffectFrame(
                            new EffectAnimation(1, 0, 1, 0, EffectAnimation.Easing.LINEAR),
                            10,
                            10,
                            0);
            zoom.show(moving);
            fade.show(neutral);
            assertEquals(zoomAsset.composition().renderer(1), values.get().getLast());
            assertFalse(values.get().contains(fadeAsset.renderer()));
            assertTrue(
                    values.get().size() < zoomAsset.controlBytes(),
                    "Sparse controls should reduce writer passes");
            fade.show(
                    new ClientEffectFrame(
                            new EffectAnimation(1, 0, .5, 40, EffectAnimation.Easing.LINEAR),
                            10,
                            10,
                            0));
            assertEquals(zoomAsset.composition().renderer(3), values.get().getLast());
            assertEquals(
                    1, values.get().stream().filter(k -> k.value().contains("/render/")).count());
            // Multiple independently authored effects in the same channel retain their separate
            // path.
            var anotherAsset = composed("other-fade", EffectCurveAsset.Kind.FADE);
            var another = manager.acquire(player, anotherAsset);
            another.show(
                    new ClientEffectFrame(
                            new EffectAnimation(0, 1, 0, 40, EffectAnimation.Easing.LINEAR),
                            10,
                            10,
                            0));
            assertTrue(values.get().contains(zoomAsset.renderer()));
            assertTrue(values.get().contains(fadeAsset.renderer()));
            assertTrue(values.get().contains(anotherAsset.renderer()));
            another.close();
            assertEquals(zoomAsset.composition().renderer(3), values.get().getLast());
            doAnswer(
                            i -> {
                                values.set(List.copyOf(i.getArgument(0)));
                                throw new IllegalStateException("applied then failed");
                            })
                    .when(effects)
                    .set(any());
            assertThrows(IllegalStateException.class, () -> fade.show(neutral));
            doAnswer(
                            i -> {
                                values.set(List.copyOf(i.getArgument(0)));
                                return true;
                            })
                    .when(effects)
                    .set(any());
            manager.clear();
            assertEquals(
                    List.of(external),
                    values.get(),
                    "Shared and uncertain renderer keys must be removed");
        }
    }

    private ClientEffectAsset composed(String name, EffectCurveAsset.Kind kind) {
        return EffectTestAssets.client(name, kind);
    }

    private ClientEffectAsset asset() {
        return composed("control", EffectCurveAsset.Kind.ZOOM);
    }

    @Test
    void controlUpdatesAreAtomicOrderedAndPreserveUnrelatedEffects() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(Player.class);
            var world = mock(World.class);
            when(player.getWorld()).thenReturn(world);
            when(world.getGameTime()).thenReturn(23999L);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var effects = mock(PlayerPostEffects.class);
            when(player.postEffects()).thenReturn(effects);
            var external = Key.key("other:effect");
            var values = new AtomicReference<List<Key>>(List.of(external));
            when(effects.values()).thenAnswer(i -> values.get());
            when(effects.set(any()))
                    .thenAnswer(i -> {
                        values.set(List.copyOf(i.getArgument(0)));
                        return true;
                    });
            var manager = new ClientEffectLeases();
            var lease = manager.acquire(player, asset());
            assertThrows(IllegalStateException.class, () -> manager.acquire(player, asset()));
            var animation = new EffectAnimation(1, 2, .25, 60, EffectAnimation.Easing.SMOOTH);
            lease.show(animation);
            assertEquals(external, values.get().getFirst());
            assertEquals(asset().composition().renderer(1), values.get().getLast());
            assertTrue(values.get().size() < asset().controlBytes() + 2);
            verify(effects, times(1)).set(any());
            var key = Key.key("test:fade");
            var scalar = composed("fade", EffectCurveAsset.Kind.FADE);
            var fade = manager.acquire(player, scalar);
            fade.show(new EffectAnimation(0, 1, 0, 40, EffectAnimation.Easing.LINEAR));
            var otherKey = Key.key("test:vignette");
            var otherScalar = composed("vignette", EffectCurveAsset.Kind.VIGNETTE);
            var vignette = manager.acquire(player, otherScalar);
            vignette.show(new EffectAnimation(1, 0, .5, 20, EffectAnimation.Easing.SMOOTH));
            assertEquals(asset().composition().renderer(19), values.get().getLast());
            assertThrows(IllegalStateException.class, () -> manager.acquire(player, scalar));
            lease.show(animation);
            assertEquals(asset().composition().renderer(19), values.get().getLast());
            fade.close();
            assertFalse(values.get().contains(scalar.renderer()));
            assertEquals(asset().composition().renderer(17), values.get().getLast());
            vignette.close();
            var changed = new ArrayList<>(values.get());
            var later = Key.key("other:later");
            changed.add(later);
            values.set(changed);
            lease.show(animation);
            assertEquals(List.of(external, later), values.get().subList(0, 2));
            lease.close();
            assertEquals(List.of(external, later), values.get());
            assertTrue(lease.closed());
            assertThrows(IllegalStateException.class, () -> lease.show(animation));
            var old = manager.acquire(player, asset());
            manager.clear();
            var current = manager.acquire(player, asset());
            current.show(animation);
            var before = values.get();
            old.close();
            assertEquals(before, values.get());
            doThrow(new IllegalStateException("transport failure")).when(effects).set(any());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            current.show(
                                    new EffectAnimation(
                                            1, 3, .5, 20, EffectAnimation.Easing.LINEAR)));
            doAnswer(
                            i -> {
                                values.set(List.copyOf(i.getArgument(0)));
                                return true;
                            })
                    .when(effects)
                    .set(any());
            current.close();
            assertEquals(
                    List.of(external, later),
                    values.get(),
                    "Failed updates retain cleanup ownership of the old bundle");
            var applied = manager.acquire(player, asset());
            applied.show(animation);
            doAnswer(
                            i -> {
                                values.set(List.copyOf(i.getArgument(0)));
                                throw new IllegalStateException("failure after apply");
                            })
                    .when(effects)
                    .set(any());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            applied.show(
                                    new EffectAnimation(
                                            1, 3, .5, 20, EffectAnimation.Easing.LINEAR)));
            doAnswer(
                            i -> {
                                values.set(List.copyOf(i.getArgument(0)));
                                return true;
                            })
                    .when(effects)
                    .set(any());
            applied.close();
            assertEquals(List.of(external, later), values.get());
        }
    }
}

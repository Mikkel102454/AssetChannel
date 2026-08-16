package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Hud;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;

final class HudSessionImpl implements HudSession {
    final HudManager manager;
    final UUID id = UUID.randomUUID(), playerId;
    final Hud template;
    final String slot;
    final int priority;
    final boolean resumeInterrupted, fallback;
    final long order;
    Map<String,Component> variables;
    HudSessionState state = HudSessionState.QUEUED;
    long remainingTicks;
    BossBar bossBar;

    HudSessionImpl(HudManager manager, Player player, Hud template, String slot, int priority,
                   boolean resumeInterrupted, boolean fallback, Map<String,Component> variables, long order) {
        this.manager=manager; this.playerId=player.getUniqueId(); this.template=template; this.slot=slot;
        this.priority=priority; this.resumeInterrupted=resumeInterrupted; this.fallback=fallback;
        this.variables=variables; this.order=order; this.remainingTicks=template.durationTicks();
    }

    void activate() { state=HudSessionState.ACTIVE; display(); }
    void suspend() { hide(); state=HudSessionState.SUSPENDED; }
    void tick() {
        if (state != HudSessionState.ACTIVE) return;
        Player player = Bukkit.getPlayer(playerId); if (player == null) { manager.close(this, false); return; }
        if (template.surface() == HudSurface.ACTION_BAR) player.sendActionBar(manager.renderer.render(template, variables, fallback));
        if (remainingTicks > 0 && --remainingTicks == 0) manager.close(this, true);
    }
    void display() {
        Player player=Bukkit.getPlayer(playerId); if(player==null)return;
        Component main=manager.renderer.render(template,variables,fallback);
        switch(template.surface()) {
            case BOSS_BAR -> { bossBar=BossBar.bossBar(main,template.bossProgress(),template.bossColor(),template.bossOverlay()); player.showBossBar(bossBar); }
            case ACTION_BAR -> player.sendActionBar(main);
            case TITLE -> player.showTitle(Title.title(main,manager.renderer.subtitle(template,variables,fallback),
                    Title.Times.times(Duration.ofMillis(template.fadeInTicks()*50),Duration.ofMillis(template.stayTicks()*50),Duration.ofMillis(template.fadeOutTicks()*50))));
        }
    }
    void hide() {
        Player player=Bukkit.getPlayer(playerId); if(player==null)return;
        if(bossBar!=null){player.hideBossBar(bossBar);bossBar=null;}
        else if(template.surface()==HudSurface.ACTION_BAR)player.sendActionBar(Component.empty());
        else if(template.surface()==HudSurface.TITLE)player.clearTitle();
    }
    @Override public UUID id(){return id;} @Override public UUID playerId(){return playerId;}
    @Override public String templateId(){return template.getId();} @Override public String slot(){return slot;}
    @Override public HudSurface surface(){return template.surface();} @Override public HudSessionState state(){return state;}
    @Override public Duration remainingDuration(){return Duration.ofMillis(remainingTicks*50);}
    @Override public boolean updateVariables(Map<String,Component> changes){
        AssetChannelApiImpl.requireMainThread(); Objects.requireNonNull(changes,"variables");
        if(state==HudSessionState.CLOSED || !template.variables().keySet().containsAll(changes.keySet()))return false;
        Map<String,Component> merged=new LinkedHashMap<>(variables); merged.putAll(changes);
        Map<String,Component> resolved=manager.renderer.resolve(template,merged); if(resolved==null)return false;
        variables=resolved; if(state==HudSessionState.ACTIVE){hide();display();} return true;
    }
    @Override public void close(){AssetChannelApiImpl.requireMainThread();manager.close(this,true);}
}

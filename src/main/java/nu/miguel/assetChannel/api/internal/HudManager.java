package nu.miguel.assetChannel.api.internal;

import net.kyori.adventure.text.Component;
import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.Hud;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

final class HudManager {
    private static final int QUEUE_LIMIT=32;
    private final AssetChannelApiImpl api;
    private Map<String,Hud> templates=Map.of();
    private Map<String,HudAsset> descriptors=Map.of();
    final Map<UUID,HudSessionImpl> sessions=new LinkedHashMap<>();
    private final Map<PlayerSurface,HudSessionImpl> active=new HashMap<>();
    private final Map<PlayerSurface,List<HudSessionImpl>> queues=new HashMap<>();
    HudRenderer renderer=new HudRenderer(Map.of());
    private long order;
    private BukkitTask ticker;

    HudManager(AssetChannelApiImpl api){this.api=api;}
    void load(Collection<Hud> values, Map<String,IconAsset> icons){
        clear(); Map<String,Hud> next=new TreeMap<>(); Map<String,HudAsset> desc=new TreeMap<>();
        HudRenderer nextRenderer=new HudRenderer(icons);
        for(Hud hud:values){next.put(hud.getId(),hud);desc.put(hud.getId(),hud.descriptor(nextRenderer::parseAssetText));}
        templates=Map.copyOf(next);descriptors=Map.copyOf(desc);renderer=nextRenderer;
    }
    Collection<HudAsset> huds(){return descriptors.values();} Optional<HudAsset> hud(String id){return Optional.ofNullable(descriptors.get(normalize(id)));}
    Optional<HudSession> session(UUID id){return Optional.ofNullable(sessions.get(id));}
    Optional<HudSession> session(Player player,String slot){return sessions.values().stream().filter(s->s.playerId.equals(player.getUniqueId())&&s.slot.equalsIgnoreCase(slot)).map(s->(HudSession)s).findFirst();}
    Collection<HudSession> sessions(){return List.copyOf(sessions.values());}
    HudDisplayResult show(Player player,String id,HudRequest request){
        AssetChannelApiImpl.requireMainThread();Objects.requireNonNull(player,"player");Objects.requireNonNull(request,"request");
        Hud template=templates.get(normalize(id)); if(template==null)return HudDisplayResult.INVALID_VARIABLES;
        Map<String,Component> variables=renderer.resolve(template,request.variables());if(variables==null)return HudDisplayResult.INVALID_VARIABLES;
        boolean fallback=!api.isPackReady(player.getUniqueId());
        if(fallback&&template.fallback()==Hud.Fallback.FAIL)return HudDisplayResult.PACK_NOT_READY;
        String slot=request.slot().orElse(template.slot()).toLowerCase(Locale.ROOT);
        if(!slot.matches("[a-z0-9][a-z0-9._-]*"))return HudDisplayResult.INVALID_VARIABLES;
        replaceSlot(player.getUniqueId(),slot);
        HudSessionImpl created=new HudSessionImpl(this,player,template,slot,request.priority().orElse(template.priority()),
                request.resumeInterrupted().orElse(template.resumeInterrupted()),fallback,variables,order++);
        sessions.put(created.id,created);PlayerSurface key=new PlayerSurface(player.getUniqueId(),template.surface());HudSessionImpl current=active.get(key);
        HudDisplayResult result;
        if(current==null){active.put(key,created);created.activate();result=fallback?HudDisplayResult.TEXT_FALLBACK:HudDisplayResult.DISPLAYED;}
        else if(created.priority>current.priority){if(current.resumeInterrupted){current.suspend();enqueue(key,current);}else close(current,false);active.put(key,created);created.activate();result=fallback?HudDisplayResult.TEXT_FALLBACK:HudDisplayResult.DISPLAYED;}
        else{enqueue(key,created);result=HudDisplayResult.QUEUED;}
        ensureTicker();return result;
    }
    boolean hide(Player player,String slot){
        AssetChannelApiImpl.requireMainThread();boolean[] found={false};
        for(HudSessionImpl s:List.copyOf(sessions.values()))if(s.playerId.equals(player.getUniqueId())&&s.slot.equalsIgnoreCase(slot)){found[0]=true;close(s,true);}return found[0];
    }
    void close(HudSessionImpl session,boolean promote){
        if(session.state==HudSessionState.CLOSED)return;PlayerSurface key=new PlayerSurface(session.playerId,session.template.surface());
        boolean wasActive=active.remove(key,session);queues.computeIfPresent(key,(k,q)->{q.remove(session);return q.isEmpty()?null:q;});
        session.hide();session.state=HudSessionState.CLOSED;sessions.remove(session.id);
        if(wasActive&&promote)promote(key);
        if(sessions.isEmpty()&&ticker!=null){ticker.cancel();ticker=null;}
    }
    void clear(){for(HudSessionImpl s:List.copyOf(sessions.values()))close(s,false);active.clear();queues.clear();if(ticker!=null){ticker.cancel();ticker=null;}}
    void onQuit(UUID player){for(HudSessionImpl s:List.copyOf(sessions.values()))if(s.playerId.equals(player))close(s,false);}
    void packReadyChanged(UUID player, boolean ready) {
        for (HudSessionImpl session : List.copyOf(sessions.values())) {
            if (!session.playerId.equals(player) || session.fallback == !ready) continue;
            if (!ready && session.template.fallback() == Hud.Fallback.FAIL) { close(session, true); continue; }
            session.fallback = !ready;
            if (session.state == HudSessionState.ACTIVE) session.displayUpdate();
        }
    }
    private void promote(PlayerSurface key){List<HudSessionImpl> q=queues.get(key);if(q==null||q.isEmpty())return;sort(q);HudSessionImpl next=q.removeFirst();if(q.isEmpty())queues.remove(key);active.put(key,next);next.activate();}
    private void enqueue(PlayerSurface key,HudSessionImpl value){
        List<HudSessionImpl> q=queues.computeIfAbsent(key,k->new ArrayList<>());q.add(value);sort(q);
        if(q.size()>QUEUE_LIMIT){HudSessionImpl dropped=q.stream().min(Comparator.comparingInt((HudSessionImpl s)->s.priority).thenComparingLong(s->s.order)).orElseThrow();q.remove(dropped);dropped.state=HudSessionState.CLOSED;sessions.remove(dropped.id);}
    }
    private static void sort(List<HudSessionImpl> q){q.sort(Comparator.comparingInt((HudSessionImpl s)->s.priority).reversed().thenComparingLong(s->s.order));}
    private void replaceSlot(UUID player,String slot){for(HudSessionImpl s:List.copyOf(sessions.values()))if(s.playerId.equals(player)&&s.slot.equals(slot))close(s,true);}
    private void ensureTicker(){if(ticker==null)ticker=Bukkit.getScheduler().runTaskTimer(Main.plugin,()->List.copyOf(active.values()).forEach(HudSessionImpl::tick),1,1);}
    private static String normalize(String id){return id==null?"":id.toLowerCase(Locale.ROOT);}
    private record PlayerSurface(UUID player,HudSurface surface){}
}

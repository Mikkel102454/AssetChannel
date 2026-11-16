package nu.miguel.assetChannel.pack;

import com.google.gson.*;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Pack icon, metadata sections and version overlays from an optional native pack root. */
public final class PackExtras implements Asset {
    private final Map<String,byte[]> files;
    private final JsonObject metadata;
    private final ImportedResources resources;
    private PackExtras(Map<String,byte[]> files,JsonObject metadata,ImportedResources resources){this.files=files;this.metadata=metadata;this.resources=resources;}
    public ImportedResources resources(){return resources;}
    public static PackExtras scan(Path root,Path extraAssets)throws IOException {
        Map<String,byte[]> files=new TreeMap<>();
        if(Files.exists(root))try(var paths=Files.walk(root)){
            for(Path path:paths.toList()) {
                if(Files.isSymbolicLink(path))throw new IllegalArgumentException("Import symlinks unsupported: "+path);
                if(!Files.isRegularFile(path))continue;
                String relative=root.relativize(path).toString().replace('\\','/');
                if(!relative.matches("[a-z0-9/._-]+")||Arrays.asList(relative.split("/")).contains(".."))throw new IllegalArgumentException("Invalid pack path: "+relative);
                files.put(relative,Files.readAllBytes(path));
            }
        }
        JsonObject meta=files.containsKey("pack.mcmeta")?JsonParser.parseString(new String(files.get("pack.mcmeta"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject():new JsonObject();
        List<Path> layers=new ArrayList<>(List.of(root.resolve("assets"),extraAssets));
        if(meta.has("overlays"))for(JsonElement raw:meta.getAsJsonObject("overlays").getAsJsonArray("entries")) {
            JsonObject entry=raw.getAsJsonObject();String directory=entry.get("directory").getAsString();
            if(!directory.matches("[a-z0-9_-]+"))throw new IllegalArgumentException("Invalid overlay directory: "+directory);
            JsonElement min=entry.get("min_format"),max=entry.get("max_format");
            if(entry.has("formats")) {
                JsonElement format=entry.get("formats");
                if(format.isJsonObject()){min=format.getAsJsonObject().get("min_inclusive");max=format.getAsJsonObject().get("max_inclusive");}
                else if(format.isJsonArray()){min=format.getAsJsonArray().get(0);max=format.getAsJsonArray().get(1);}
                else {min=format;max=format;}
            }
            if(compare(min,false)<=0&&compare(max,true)>=0)layers.add(root.resolve(directory).resolve("assets"));
        }
        return new PackExtras(files,meta,ImportedResources.scanLayers(layers));
    }
    private static int compare(JsonElement version,boolean upper) {
        if(version==null)return upper?1:-1;
        int major=version.isJsonArray()?version.getAsJsonArray().get(0).getAsInt():version.getAsInt();
        int minor=version.isJsonArray()?version.getAsJsonArray().get(1).getAsInt():(upper?Integer.MAX_VALUE:0);
        return major==97?Integer.compare(minor,1):Integer.compare(major,97);
    }
    @Override public String getId(){return "native-pack-extras";}
    @Override public boolean build(TexturePack pack) {
        try {
            for(var entry:files.entrySet()) {
                if(entry.getKey().equals("pack.mcmeta")||entry.getKey().startsWith("assets/"))continue;
                Path target=pack.getRootPath().resolve(entry.getKey());
                Files.createDirectories(target.getParent());Files.write(target,entry.getValue(),StandardOpenOption.CREATE_NEW);
            }
            Path metaPath=pack.getRootPath().resolve("pack.mcmeta");
            JsonObject result=JsonParser.parseString(Files.readString(metaPath)).getAsJsonObject();
            for(var entry:metadata.entrySet())if(!entry.getKey().equals("pack"))result.add(entry.getKey(),entry.getValue());
            if(metadata.has("pack")&&metadata.getAsJsonObject("pack").has("description"))result.getAsJsonObject("pack").add("description",metadata.getAsJsonObject("pack").get("description"));
            Files.writeString(metaPath,new GsonBuilder().setPrettyPrinting().create().toJson(result));return true;
        }catch(IOException error){throw new IllegalStateException("Could not stage pack extras",error);}
    }
}

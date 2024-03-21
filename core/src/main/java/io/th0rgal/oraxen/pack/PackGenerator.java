package io.th0rgal.oraxen.pack;

import com.ticxo.modelengine.api.ModelEngineAPI;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.config.Settings;
import io.th0rgal.oraxen.font.FontManager;
import io.th0rgal.oraxen.font.Glyph;
import io.th0rgal.oraxen.utils.ModelEngineUtils;
import io.th0rgal.oraxen.utils.VersionUtil;
import io.th0rgal.oraxen.utils.customarmor.CustomArmor;
import io.th0rgal.oraxen.utils.customarmor.CustomArmorType;
import io.th0rgal.oraxen.utils.customarmor.ShaderArmorTextures;
import io.th0rgal.oraxen.utils.customarmor.TrimArmorDatapack;
import io.th0rgal.oraxen.utils.logs.Logs;
import net.kyori.adventure.key.Key;
import team.unnamed.creative.BuiltResourcePack;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.atlas.Atlas;
import team.unnamed.creative.base.Writable;
import team.unnamed.creative.font.Font;
import team.unnamed.creative.font.FontProvider;
import team.unnamed.creative.lang.Language;
import team.unnamed.creative.metadata.pack.PackMeta;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader;
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter;
import team.unnamed.creative.sound.SoundRegistry;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class PackGenerator {

    public static Path externalPacks = OraxenPlugin.get().packPath().resolve("external_packs");
    private static final Path assetsFolder = OraxenPlugin.get().packPath().resolve("assets");
    private ResourcePack resourcePack;
    private BuiltResourcePack builtPack;
    private CustomArmor customArmorHandler;

    public PackGenerator() {
        generateDefaultPaths();
        PackDownloader.downloadDefaultPack();
        if (CustomArmorType.getSetting().equals(CustomArmorType.SHADER)) customArmorHandler = new ShaderArmorTextures();
        else if (CustomArmorType.getSetting().equals(CustomArmorType.TRIMS)) new TrimArmorDatapack();
    }

    public void generatePack() {
        Logs.logInfo("Generating resourcepack...");
        resourcePack = MinecraftResourcePackReader.minecraft().readFromDirectory(OraxenPlugin.get().packPath().toFile());
        resourcePack.removeUnknownFile("pack.zip");

        OraxenPlugin.get().resourcePack(resourcePack);
        addImportPacks();
        importModelEnginePack();

        addItemPackFiles();
        addGlyphFiles();
        customArmorHandler.generateNeededFiles();
        if (Settings.HIDE_SCOREBOARD_NUMBERS.toBool()) hideScoreboardNumbers();
        if (Settings.HIDE_SCOREBOARD_BACKGROUND.toBool()) hideScoreboardBackground();

        for (Map.Entry<String, Writable> entry : new HashSet<>(resourcePack.unknownFiles().entrySet()))
            if (entry.getKey().startsWith("external_packs/")) resourcePack.removeUnknownFile(entry.getKey());

        removeExcludedFileExtensions();

        MinecraftResourcePackWriter.minecraft().writeToZipFile(OraxenPlugin.get().packPath().resolve("pack.zip").toFile(), resourcePack);

        builtPack = MinecraftResourcePackWriter.minecraft().build(resourcePack);
        Logs.logSuccess("Finished generating resourcepack!", true);
    }

    private void importModelEnginePack() {
        if (!ModelEngineUtils.isModelEngineEnabled()) return;
        File megPack = ModelEngineAPI.getAPI().getDataFolder().toPath().resolve("resource pack.zip").toFile();
        if (!megPack.exists()) return;
        mergePack(MinecraftResourcePackReader.minecraft().readFromZipFile(megPack));
        Logs.logSuccess("Imported ModelEngine pack successfully!");
    }

    private void addGlyphFiles() {
        Map<Key, List<FontProvider>> fontGlyphs = new HashMap<>();
        for (Glyph glyph : OraxenPlugin.get().fontManager().glyphs()) {
            if (!glyph.hasBitmap()) fontGlyphs.compute(glyph.font(), (key, providers) -> {
                if (providers == null) providers = new ArrayList<>();
                providers.add(glyph.fontProvider());
                return providers;
            });
        }

        for (FontManager.GlyphBitMap glyphBitMap : FontManager.glyphBitMaps.values()) {
            fontGlyphs.compute(glyphBitMap.font(), (key, providers) -> {
                if (providers == null) providers = new ArrayList<>();
                providers.add(glyphBitMap.fontProvider());
                return providers;
            });
        }

        for (Map.Entry<Key, List<FontProvider>> entry : fontGlyphs.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            resourcePack.font(Font.font(entry.getKey(), entry.getValue()));
        }
    }

    private void hideScoreboardNumbers() {
        resourcePack.unknownFile("assets/minecraft/shaders/core/rendertype_text.json", ShaderUtils.ScoreboardNumbers.json());
        resourcePack.unknownFile("assets/minecraft/shaders/post/deferred_text.vsh", ShaderUtils.ScoreboardNumbers.vsh());
    }

    private void hideScoreboardBackground() {
        String fileName = VersionUtil.atOrAbove("1.20.1") ? "rendertype_gui.vsh" : "position_color.fsh";
        Writable writable = VersionUtil.atOrAbove("1.20.1") ? ShaderUtils.ScoreboardBackground.modernFile() : ShaderUtils.ScoreboardBackground.legacyFile();
        resourcePack.unknownFile("assets/minecraft/shaders/core/" + fileName, writable);
    }

    private void addImportPacks() {
        for (File file : externalPacks.toFile().listFiles()) {
            if (file == null) continue;
            if (file.isDirectory()) {
                Logs.logInfo("Importing pack " + file.getName() + "...");
                mergePack(MinecraftResourcePackReader.minecraft().readFromDirectory(file));
            } else if (file.getName().endsWith(".zip")) {
                Logs.logInfo("Importing zipped pack " + file.getName() + "...");
                mergePack(MinecraftResourcePackReader.minecraft().readFromZipFile(file));
            } else {
                Logs.logError("Skipping unknown file " + file.getName() + " in imports folder");
                Logs.logError("File is neither a directory nor a zip file");
            }
        }
    }

    private void mergePack(ResourcePack importedPack) {
        importedPack.textures().forEach(resourcePack::texture);
        importedPack.sounds().forEach(resourcePack::sound);
        importedPack.unknownFiles().forEach(resourcePack::unknownFile);

        PackMeta packMeta = importedPack.packMeta() != null ? importedPack.packMeta() : resourcePack.packMeta();
        if (packMeta != null) resourcePack.packMeta(packMeta);
        Writable packIcon = importedPack.icon() != null ? importedPack.icon() : resourcePack.icon();
        if (packIcon != null) resourcePack.icon(packIcon);

        importedPack.models().forEach(model -> {
            Model baseModel = resourcePack.model(model.key());
            if (baseModel != null) model.overrides().addAll(baseModel.overrides());
            resourcePack.model(model);
        });

        importedPack.fonts().forEach(font -> {
            Font baseFont = resourcePack.font(font.key());
            if (baseFont != null) font.providers().addAll(baseFont.providers());
            resourcePack.font(font);
        });

        importedPack.soundRegistries().forEach(soundRegistry -> {
            SoundRegistry baseRegistry = resourcePack.soundRegistry(soundRegistry.namespace());
            if (baseRegistry != null) soundRegistry.sounds().addAll(baseRegistry.sounds());
            resourcePack.soundRegistry(soundRegistry);
        });

        importedPack.atlases().forEach(atlas -> {
            Atlas baseAtlas = resourcePack.atlas(atlas.key());
            if (baseAtlas != null) atlas.sources().forEach( source -> baseAtlas.toBuilder().addSource(source));
            resourcePack.atlas(atlas);
        });

        importedPack.languages().forEach(language -> {
            Language baseLanguage = resourcePack.language(language.key());
            if (baseLanguage != null) baseLanguage.translations().putAll(language.translations());
            resourcePack.language(language);
        });
    }

    public BuiltResourcePack builtPack() {
        return builtPack;
    }

    private static void generateDefaultPaths() {
        externalPacks.toFile().mkdirs();
        assetsFolder.resolve("minecraft/textures").toFile().mkdirs();
        assetsFolder.resolve("minecraft/models").toFile().mkdirs();
        assetsFolder.resolve("minecraft/sounds").toFile().mkdirs();
        assetsFolder.resolve("minecraft/font").toFile().mkdirs();
        assetsFolder.resolve("minecraft/lang").toFile().mkdirs();
    }

    private void addItemPackFiles() {
        ModelGenerator.generateBaseItemModels();
        ModelGenerator.generateItemModels();
        AtlasGenerator.generateAtlasFile();
    }

    private final static Set<String> ignoredExtensions = new HashSet<>(Arrays.asList(".json", ".png", ".mcmeta"));
    private void removeExcludedFileExtensions() {
        for (String extension : Settings.EXCLUDED_FILE_EXTENSIONS.toStringList()) {
            extension = extension.startsWith(".") ? extension : "." + extension;
            if (ignoredExtensions.contains(extension)) continue;
            for (Map.Entry<String, Writable> entry : new HashSet<>(resourcePack.unknownFiles().entrySet())) {
                if (entry.getKey().endsWith(extension)) resourcePack.removeUnknownFile(entry.getKey());
            }
        }
    }
}
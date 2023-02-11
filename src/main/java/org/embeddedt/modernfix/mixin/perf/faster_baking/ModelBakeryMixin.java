package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.model.multipart.Multipart;
import net.minecraft.client.renderer.model.multipart.Selector;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.embeddedt.modernfix.ModernFix.LOGGER;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin implements IExtendedModelBakery {
    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;

    @Shadow @Final private Map<ResourceLocation, IBakedModel> bakedTopLevelModels;

    @Shadow @Deprecated @Nullable public abstract IBakedModel bake(ResourceLocation pLocation, IModelTransform pTransform);

    @Shadow private Map<ResourceLocation, Pair<AtlasTexture, AtlasTexture.SheetData>> atlasPreparations;

    @Shadow @Nullable private SpriteMap atlasSet;

    @Shadow @Nullable public abstract IBakedModel getBakedModel(ResourceLocation pLocation, IModelTransform pTransform, Function<RenderMaterial, TextureAtlasSprite> textureGetter);

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow @Final private Map<Triple<ResourceLocation, TransformationMatrix, Boolean>, IBakedModel> bakedCache;

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;pop()V"))
    private void bakeModels(IProfiler pProfiler, int p_i226056_4_, CallbackInfo ci) {
        pProfiler.popPush("atlas");
        Minecraft.getInstance().executeBlocking(() -> {
            for(Pair<AtlasTexture, AtlasTexture.SheetData> pair : this.atlasPreparations.values()) {
                AtlasTexture atlastexture = pair.getFirst();
                AtlasTexture.SheetData atlastexture$sheetdata = pair.getSecond();
                atlastexture.reload(atlastexture$sheetdata);
            }
        });
        pProfiler.popPush("baking");
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Baking models"));
        this.atlasSet = new SpriteMap(this.atlasPreparations.values().stream().map(Pair::getFirst).collect(Collectors.toList()));
        IBakedModel missingModel = this.bake(MISSING_MODEL_LOCATION, ModelRotation.X0_Y0);
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, missingModel);
        this.topLevelModels.keySet().forEach((p_229350_1_) -> {
            this.bakedTopLevelModels.put(p_229350_1_, new LazyBakedModel(() -> {
                synchronized (this.bakedCache) {
                    IBakedModel ibakedmodel = null;

                    try {
                        ibakedmodel = this.bake(p_229350_1_, ModelRotation.X0_Y0);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        LOGGER.warn("Unable to bake model: '{}': {}", p_229350_1_, exception);
                    }

                    return ibakedmodel != null ? ibakedmodel : missingModel;
                }
            }));
        });
    }

    /**
     * @author embeddedt
     * @reason texture loading and baking are moved earlier in the launch process, only render thread stuff is done here
     */
    @Overwrite
    public SpriteMap uploadTextures(TextureManager pResourceManager, IProfiler pProfiler) {
        pProfiler.push("atlas_upload");
        for(Pair<AtlasTexture, AtlasTexture.SheetData> pair : this.atlasPreparations.values()) {
            AtlasTexture atlastexture = pair.getFirst();
            AtlasTexture.SheetData atlastexture$sheetdata = pair.getSecond();
            pResourceManager.register(atlastexture.location(), atlastexture);
            pResourceManager.bind(atlastexture.location());
            atlastexture.updateFilter(atlastexture$sheetdata);
        }
        pProfiler.pop();
        return this.atlasSet;
    }

    @Override
    public SpriteMap getUnfinishedAtlasSet() {
        return this.atlasSet;
    }
}

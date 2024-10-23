package rynnavinx.sspb.mixin.indium;

import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.aocalc.AoConfig;
import link.infra.indium.renderer.render.BlockRenderInfo;

import net.minecraft.block.BlockState;
import net.minecraft.block.DirtPathBlock;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import rynnavinx.sspb.client.SSPBClientMod;


@Pseudo @Mixin(AoCalculator.class)
public abstract class MixinAoCalculator {

    @Final @Shadow(remap = false)
    private BlockRenderInfo blockInfo;


    @Unique
    private float sspb$modifyW1(float w1){
        BlockState blockState =  blockInfo.blockState;
        boolean onlyAffectPathBlocks = SSPBClientMod.options().onlyAffectPathBlocks;

        if((!onlyAffectPathBlocks && blockState.isTransparent(blockInfo.blockView, blockInfo.blockPos)) ||
                (onlyAffectPathBlocks && blockState.getBlock() instanceof DirtPathBlock)){
            return (w1 * SSPBClientMod.options().getShadowynessCompliment()) + (SSPBClientMod.options().getShadowyness());
        }
        return w1;
    }


    @ModifyVariable(method = "blendedInsetFace", at = @At("STORE"), ordinal = 0, remap = false)
    private float modifyBlendedInsetFaceW1(float w1){
        return sspb$modifyW1(w1);
    }

    @ModifyVariable(method = "gatherInsetFace", at = @At("STORE"), ordinal = 0, remap = false)
    private float modifyGatherInsetFaceW1(float w1){
        return sspb$modifyW1(w1);
    }

    @ModifyVariable(method = "compute", at = @At("STORE"), ordinal = 0, remap = false)
    private AoConfig modifyAoConfig(AoConfig config){
        if(SSPBClientMod.options().vanillaPathBlockLighting && blockInfo.blockState.getBlock() instanceof DirtPathBlock){
            return AoConfig.VANILLA;
        }
        return config;
    }
}
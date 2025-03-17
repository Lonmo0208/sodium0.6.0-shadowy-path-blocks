package rynnavinx.sspb.forge.mixin.sodium;

import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.frapi.mesh.EncodingFormat;
import me.jellysquid.mods.sodium.client.render.frapi.mesh.QuadViewImpl;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rynnavinx.sspb.common.client.SSPBClientMod;
import rynnavinx.sspb.common.client.render.frapi.aocalc.VanillaAoHelper;
import rynnavinx.sspb.common.client.util.MethodSignature;
import rynnavinx.sspb.common.mixin.minecraft.AmbientOcclusionFaceAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.BitSet;

@Mixin(SmoothLightPipeline.class)
public abstract class SmoothLightPipelineMixin {

	@Shadow(remap = false)
	@Final
	private LightDataAccess lightCache;

	@Unique
	private static MethodHandle sspb$propagatesSkylightDownHandle;

	static {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			// 正确的方法签名：1.20.1 Forge使用getBlockState()而不是直接传递BlockGetter
			sspb$propagatesSkylightDownHandle = lookup.findVirtual(
					BlockStateBase.class,
					"m_60631_",
					MethodType.methodType(boolean.class, BlockGetter.class, BlockPos.class)
			);
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException("Failed to initialize propagatesSkylightDown method handle", e);
		}
	}

	@Unique
	private boolean sspb$propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		try {
			return (boolean) sspb$propagatesSkylightDownHandle.invokeExact(
					(BlockStateBase) state,
					level,
					pos
			);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to invoke propagatesSkylightDown", e);
		}
	}

	@Unique
	private float sspb$getModifiedAOWeight(float originalWeight, BlockPos pos) {
		BlockState blockState = lightCache.getLevel().getBlockState(pos);
		boolean onlyAffectPathBlocks = SSPBClientMod.options().onlyAffectPathBlocks;

		boolean shouldModify = (!onlyAffectPathBlocks && sspb$propagatesSkylightDown(blockState, lightCache.getLevel(), pos))
				|| (onlyAffectPathBlocks && blockState.getBlock() instanceof DirtPathBlock);

		if (shouldModify) {
			float shadow = SSPBClientMod.options().getShadowyness();
			float compliment = SSPBClientMod.options().getShadowynessCompliment();
			return Mth.clamp((originalWeight * compliment) + shadow, 0.0f, 1.0f);
		}
		return originalWeight;
	}

	@ModifyVariable(
			method = "applyInsetPartialFaceVertex(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;FF[FILme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Z)V",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0,
			remap = false
	)
	private float modifyApplyInsetPartialFaceVertexN1d(float n1d, BlockPos pos, Direction dir) {
		return sspb$getModifiedAOWeight(n1d, pos);
	}

	@ModifyVariable(
			method = "applyInsetPartialFaceVertex",
			at = @At(value = "HEAD", shift = At.Shift.AFTER),
			argsOnly = true,
			ordinal = 1,
			remap = false
	)
	private float modifyApplyInsetPartialFaceVertexN2d(float n2d, BlockPos pos, Direction dir, float n1d) {
		return 1.0f - n1d; // 根据原始逻辑修正，n2d = 1 - n1d
	}

	@ModifyVariable(
			method = "gatherInsetFace(Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;ILnet/minecraft/core/Direction;Z)Lme/jellysquid/mods/sodium/client/model/light/smooth/AoFaceData;",
			at = @At(value = "STORE", ordinal = 0),
			remap = false
	)
	private float modifyGatherInsetFaceW1(float w1, ModelQuadView quad, BlockPos blockPos) {
		return sspb$getModifiedAOWeight(w1, blockPos);
	}

	@Inject(
			method = "calculate(Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)V",
			at = @At(
					value = "INVOKE",
					target = "Lme/jellysquid/mods/sodium/client/model/light/smooth/SmoothLightPipeline;applyParallelFace(Lme/jellysquid/mods/sodium/client/model/light/smooth/AoNeighborInfo;Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Z)V",
					shift = At.Shift.BEFORE
			),
			cancellable = true,
			remap = false
	)
	private void injectVanillaAoCalcForPathBlocks(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade, boolean isFluid, CallbackInfo ci) {
		if (SSPBClientMod.options().vanillaPathBlockLighting && lightCache.getLevel().getBlockState(pos).getBlock() instanceof DirtPathBlock) {
			sspb$calcVanilla((QuadViewImpl) quad, out.br, out.lm, pos, lightFace, shade);
			ci.cancel();
		}
	}

	@Unique
	private final ModelBlockRenderer.AmbientOcclusionFace sspb$vanillaCalc = new ModelBlockRenderer.AmbientOcclusionFace();
	@Unique
	private final float[] sspb$vanillaAoData = new float[Direction.values().length * 2];
	@Unique
	private final BitSet sspb$vanillaAoControlBits = new BitSet(3);
	@Unique
	private final int[] sspb$vertexData = new int[EncodingFormat.QUAD_STRIDE];

	@Unique
	private void sspb$calcVanilla(QuadViewImpl quad, float[] aoDest, int[] lightDest, BlockPos pos, Direction lightFace, boolean shade) {
		sspb$vanillaAoControlBits.clear();
		quad.toVanilla(sspb$vertexData, 0);

		BlockAndTintGetter level = lightCache.getLevel();
		VanillaAoHelper.updateShape(level, level.getBlockState(pos), pos, sspb$vertexData, lightFace, sspb$vanillaAoData, sspb$vanillaAoControlBits);
		sspb$vanillaCalc.calculate(level, level.getBlockState(pos), pos, lightFace, sspb$vanillaAoData, sspb$vanillaAoControlBits, shade);

		System.arraycopy(((AmbientOcclusionFaceAccessor) sspb$vanillaCalc).sspb$getBrightness(), 0, aoDest, 0, 4);
		System.arraycopy(((AmbientOcclusionFaceAccessor) sspb$vanillaCalc).sspb$getLightmap(), 0, lightDest, 0, 4);
	}
}
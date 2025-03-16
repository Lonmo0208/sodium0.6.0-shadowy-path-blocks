/*
 * This file contains modified parts of AoCalculator.java from
 * "Fabric Renderer - Indigo" from "Fabric API".
 *
 * Therefore, it incorporates work under the following license:
 *
	 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License");
	 * you may not use this file except in compliance with the License.
	 * You may obtain a copy of the License at
	 *
	 *     http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS,
	 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and
	 * limitations under the License.
 */

package rynnavinx.sspb.fabric.mixin.sodium;

import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.render.frapi.mesh.EncodingFormat;
import me.jellysquid.mods.sodium.client.render.frapi.mesh.QuadViewImpl;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
import java.lang.reflect.Method;
import java.util.BitSet;
import org.objectweb.asm.Opcodes;


// Same as the version of this mixin in common, but remap is not set to false in the below annotation
// Fabric crashes with remap = false here, but Forge doesn't compile without it
// Ideally I wouldn't have to copy the whole class, but I can't use a variable boolean to set remap,
// and extending the original class doesn't work with mixins
@Mixin(value = SmoothLightPipeline.class)
public abstract class SmoothLightPipelineMixin {

	@Final @Shadow(remap = false)
	private LightDataAccess lightCache;

	@Unique
	private static final MethodSignature[] sspb$propagatesSkylightDownMethodSignatures = {
			// Different mappings are used depending on loader and if the game is run in a dev environment or not
			// The equivalent mojmap and yarn intermediary method signatures MUST be given in the same order

			// Mojmap:
			new MethodSignature("propagatesSkylightDown", new Class[]{BlockGetter.class, BlockPos.class}), // 1.20.1+ method signature
			new MethodSignature("propagatesSkylightDown", new Class[]{}), // 1.21.2+ method signature

			// Yarn Intermediary:
			new MethodSignature("method_26167", new Class[]{BlockGetter.class, BlockPos.class}), // 1.20.1+ method signature
			new MethodSignature("method_26167", new Class[]{}) // 1.21.2+ method signature
	};

	@Unique
	private static MethodHandle sspb$propagatesSkylightDownHandle = null;

	@Unique
	private static int sspb$propagatesSkylightDownVersion = -1;


	static {
		MethodHandles.Lookup lookup = MethodHandles.lookup();

		// Set method handle for propagatesSkyLightDown - searches for the method, as the signature is different across versions
		Method[] blockStateMethods = BlockState.class.getMethods();
		outerLoop:
		for(int i = 0; i < sspb$propagatesSkylightDownMethodSignatures.length; ++i){
			for(Method method : blockStateMethods) {
				if(sspb$propagatesSkylightDownMethodSignatures[i].equals(MethodSignature.fromMethod(method))) {
					try {
						sspb$propagatesSkylightDownHandle = lookup.unreflect(method);
					} catch (IllegalAccessException e) {
						throw new RuntimeException(e);
					}

					// Modulus on i, so that sspb$propagatesSkylightDownVersion is set to the same value regardless of mojmap or yarn intermediary
					sspb$propagatesSkylightDownVersion = i % (sspb$propagatesSkylightDownMethodSignatures.length / 2);

					break outerLoop;
				}
			}
		}

		if(sspb$propagatesSkylightDownHandle == null){
			throw new RuntimeException("\"propagatesSkylightDown\" method not found.");
		}
	}


	@Unique
	private boolean sspb$propagatesSkylightDown(BlockBehaviour.BlockStateBase blockStateBase, BlockGetter level, BlockPos pos){
		try {
			if(sspb$propagatesSkylightDownVersion == 0){
				return (boolean) sspb$propagatesSkylightDownHandle.invoke(blockStateBase, level, pos);
			}
			else{
				return (boolean) sspb$propagatesSkylightDownHandle.invoke(blockStateBase);
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	@Unique
	private float sspb$getModifiedAOWeight(float originalWeight, BlockPos pos) {
		if (pos == null) return originalWeight;
		BlockState blockState = lightCache.getLevel().getBlockState(pos);
		boolean onlyAffectPathBlocks = SSPBClientMod.options().onlyAffectPathBlocks;

		if((!onlyAffectPathBlocks && sspb$propagatesSkylightDown(blockState, lightCache.getLevel(), pos)) ||
				(onlyAffectPathBlocks && blockState.getBlock() instanceof DirtPathBlock)){

			// Mix between actual and full shadowyness, to mix between fixed sodium lighting and bugged vanilla lighting, respectively
			return (originalWeight * SSPBClientMod.options().getShadowynessCompliment()) + SSPBClientMod.options().getShadowyness();
		}

		return originalWeight;
	}


	// Ideally the following two mixins would be done by modifying depth directly on applyParallelFace and applyNonParallelFace,
	// but the package private AoNeighborInfo in the method signature prevents me from doing that
	@ModifyVariable(
			method = "applyInsetPartialFaceVertex",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private float modifyApplyInsetPartialFaceVertexN1d(float n1d, BlockPos pos, Direction dir) {
		return sspb$getModifiedAOWeight(n1d, pos);
	}

	@ModifyVariable(
			method = "gatherInsetFace",
			at = @At(
					value = "STORE",
					opcode = Opcodes.FSTORE
			),
			ordinal = 0,
			remap = false
	)
	private float modifyGatherInsetFaceDepth(
			float depth,
			BlockPos pos,
			int vertexIdx,
			Direction face,
			boolean shade,
			float x, float y, float z
	) {
		return sspb$getModifiedAOWeight(depth, pos);
	}

	@Inject(method = "calculate", at = @At(value = "INVOKE",
			target = "Lme/jellysquid/mods/sodium/client/model/light/smooth/SmoothLightPipeline;applyParallelFace(Lme/jellysquid/mods/sodium/client/model/light/smooth/AoNeighborInfo;Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Z)V",
			shift = At.Shift.BEFORE),
			cancellable = true)
	private void injectVanillaAoCalcForPathBlocks(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade, boolean isFluid, CallbackInfo ci) {
		if (SSPBClientMod.options().vanillaPathBlockLighting
				&& lightCache.getLevel().getBlockState(pos).getBlock() instanceof DirtPathBlock) {

			if (quad instanceof QuadViewImpl) {
				sspb$calcVanilla((QuadViewImpl) quad, out.br, out.lm, pos, lightFace, shade);
				ci.cancel();
			}
		}
	}


	/*
	 * From here to the end of the file is code adapted from AoCalculator.java from
	 * "Fabric Renderer - Indigo" from "Fabric API".
	 */

	@Unique
	private final ModelBlockRenderer.AmbientOcclusionFace sspb$vanillaCalc = new ModelBlockRenderer.AmbientOcclusionFace();

	// These are what vanilla AO calc wants, per its usage in vanilla code
	// Because this instance is effectively thread-local, we preserve instances
	// to avoid making a new allocation each call.
	@Unique
	private final float[] sspb$vanillaAoData = new float[Direction.values().length * 2];
	@Unique
	private final BitSet sspb$vanillaAoControlBits = new BitSet(3);
	@Unique
	private final int[] sspb$vertexData = new int[EncodingFormat.QUAD_STRIDE];


	@Unique
	private void sspb$calcVanilla(QuadViewImpl quad, float[] aoDest, int[] lightDest, BlockPos pos, Direction lightFace, boolean shade) {
		if (quad == null || aoDest == null || lightDest == null) return;
		sspb$vanillaAoControlBits.clear();
		quad.toVanilla(sspb$vertexData, 0);

		BlockAndTintGetter level = lightCache.getLevel();

		VanillaAoHelper.updateShape(level, level.getBlockState(pos), pos, sspb$vertexData, lightFace, sspb$vanillaAoData, sspb$vanillaAoControlBits);
		sspb$vanillaCalc.calculate(level, level.getBlockState(pos), pos, lightFace, sspb$vanillaAoData, sspb$vanillaAoControlBits, shade);

		System.arraycopy(((AmbientOcclusionFaceAccessor) sspb$vanillaCalc).sspb$getBrightness(), 0, aoDest, 0, 4);
		System.arraycopy(((AmbientOcclusionFaceAccessor) sspb$vanillaCalc).sspb$getLightmap(), 0, lightDest, 0, 4);
	}
}

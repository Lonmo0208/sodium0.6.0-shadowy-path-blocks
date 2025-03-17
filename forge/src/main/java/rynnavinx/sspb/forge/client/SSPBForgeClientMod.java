package rynnavinx.sspb.forge.client;

import net.minecraftforge.fml.common.Mod;

import rynnavinx.sspb.common.client.SSPBClientMod;


@Mod("sspb")
public class SSPBForgeClientMod {

	public SSPBForgeClientMod() {
		SSPBClientMod.onInitClient();
	}
}

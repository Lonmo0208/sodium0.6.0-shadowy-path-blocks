package rynnavinx.sspb.forge.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

import rynnavinx.sspb.common.client.SSPBClientMod;


@Mod("sspb")
public class SSPBForgeClientMod {

	public SSPBForgeClientMod(IEventBus eventBus) {
		SSPBClientMod.onInitClient();
	}
}

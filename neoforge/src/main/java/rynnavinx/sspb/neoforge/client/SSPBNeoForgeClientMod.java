package rynnavinx.sspb.neoforge.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import rynnavinx.sspb.common.client.SSPBClientMod;


@Mod("sspb")
public class SSPBNeoForgeClientMod {

	public SSPBNeoForgeClientMod(IEventBus eventBus) {
		SSPBClientMod.onInitClient();
	}
}

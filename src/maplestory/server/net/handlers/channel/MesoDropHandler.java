package maplestory.server.net.handlers.channel;

import io.netty.buffer.ByteBuf;
import maplestory.channel.MapleChannel;
import maplestory.client.MapleClient;
import maplestory.player.MapleCharacter;
import maplestory.server.net.MaplePacketHandler;

public class MesoDropHandler extends MaplePacketHandler {

	private long lastDrop;
	
	public MesoDropHandler() {
		lastDrop = 0;
	}
	
	@Override
	public void handle(ByteBuf buf, MapleClient client) {
		buf.skipBytes(4);
		
		int amount = buf.readInt();
		
		if(amount < 10){
			client.sendReallowActions();
			return;
		}
		
		if(System.currentTimeMillis() - lastDrop < 200){
			return;
		}
		
		MapleCharacter chr = client.getCharacter();
		
		if(chr.getMeso() >= amount){
			chr.giveMesos(-amount);
			chr.getMap().dropMesos(amount, chr.getPosition(), chr);
			lastDrop = System.currentTimeMillis();
		}
		
		client.sendReallowActions();
		
	}

}

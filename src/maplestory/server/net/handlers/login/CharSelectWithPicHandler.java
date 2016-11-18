package maplestory.server.net.handlers.login;

import java.net.InetAddress;

import io.netty.buffer.ByteBuf;
import lombok.SneakyThrows;
import maplestory.channel.MapleChannel;
import maplestory.client.MapleClient;
import maplestory.server.MapleStory;
import maplestory.server.net.MaplePacketHandler;
import maplestory.server.net.PacketFactory;

public class CharSelectWithPicHandler extends MaplePacketHandler {

	@SneakyThrows
	@Override
	public void handle(ByteBuf buf, MapleClient client) {
		String pic = readMapleAsciiString(buf);
		int charId = buf.readInt();
		//String macs = readString(buf); //This works but its commented out because we don't use it atm
		
		if(client.checkPic(pic)){
			InetAddress address = InetAddress.getByName(MapleStory.getServerConfig().getChannelServerIp());
			
			MapleChannel channel = client.getChannel();
			
			client.sendChannelAddress(address, channel.getPort(), charId);
		}else{
			client.sendPacket(PacketFactory.getWrongPic());
		}
		
	}

}

package maplestory.server.net.handlers.channel;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Getter;
import maplestory.client.MapleClient;
import maplestory.player.MapleCharacter;
import maplestory.quest.MapleQuest;
import maplestory.quest.MapleQuestInstance;
import maplestory.script.MapleScript;
import maplestory.server.net.MaplePacketHandler;

public class QuestActionHandler extends MaplePacketHandler {

	@Override
	public void handle(ByteBuf buf, MapleClient client) {
		byte aid = buf.readByte();
		ActionType action = ActionType.getType(aid);
		
		if(action == null){
			client.getLogger().warn("Unknown action id "+aid);
			return;
		}
		
		short questId = buf.readShort();
		MapleCharacter chr = client.getCharacter();
		MapleQuest quest = MapleQuest.getQuest(questId);
		
		int npc = 0;
		
		if(action != ActionType.FORFEIT){
			npc = buf.readInt();
			
			MapleQuestInstance inst = chr.getQuest(questId);
			inst.setNpc(npc);
		}
		
		if(action == ActionType.START){
			if(quest.canStart(chr, npc)){
				quest.start(chr, npc);
			}
		}else if(action == ActionType.COMPLETE){
			buf.skipBytes(4);
			
			if(quest.canComplete(chr, npc)){
				if(buf.readableBytes() >= 2){
					int selection = buf.readShort();
					
					quest.complete(chr, npc, selection);
				}else{
					quest.complete(chr, npc);
				}
			}
			
		}else if(action == ActionType.FORFEIT){
			quest.forfeit(chr);
		}else if(action == ActionType.START_WITH_SCRIPT){
			if(quest.canStart(chr, npc)){
				MapleScript script = new MapleScript("scripts/quest/"+questId+".js", "scripts/quest/fallback.js");
				
				chr.openQuestNpc(script, questId, npc, false);
			}
		}else if(action == ActionType.END_WITH_SCRIPT){
			if(quest.canComplete(chr, npc)){
				MapleScript script = new MapleScript("scripts/quest/"+questId+".js", "scripts/quest/fallback.js");
				
				chr.openQuestNpc(script, questId, npc, true);
			}
		}
	}
	
	@AllArgsConstructor
	public static enum ActionType {
		START(1),
		COMPLETE(2),
		FORFEIT(3),
		START_WITH_SCRIPT(4),
		END_WITH_SCRIPT(5);
		
		@Getter
		private final int id;
		
		public static ActionType getType(byte action){
			for(ActionType type : values()){
				if(type.id == action){
					return type;
				}
			}
			return null;
		}
	}

}

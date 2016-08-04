package maplestory.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;

import constants.LoginStatus;
import constants.ServerConstants;
import database.MapleDatabase;
import database.QueryResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import maplestory.channel.MapleChannel;
import maplestory.player.MapleCharacter;
import maplestory.server.MapleServer;
import maplestory.server.net.PacketFactory;
import maplestory.server.net.SendOpcode;
import maplestory.server.security.AccountEncryption;
import maplestory.server.security.MapleAESOFB;
import maplestory.util.Hex;
import maplestory.world.World;

public class MapleClient {

	private static final Map<Integer, String> accountNames = new HashMap<>();
	
	private ReentrantLock connectionLock = new ReentrantLock(true);
	@Getter
	private Channel connection;
	
	@Getter
	private MapleAESOFB cryptSend, cryptRecv;
	
	@Getter
	private String username;
	
	@Setter @Getter
	private int worldId;
	
	private int channelId;
	
	@Getter
	private int id;
	
	@Getter
	private String pic;

	@Getter @Setter
	private MapleCharacter character;
	
	private LoginStatus loginStatus;
	
	private int gmLevel;
	
	@Getter
	private boolean changingChannels;
	
	@Getter
	private Logger logger;
	
	@Getter
	private String loginMessage;
	
	@Getter
	private long ping = 0;
	
	private long pingTracker = 0;
	
	public MapleClient(Channel connection, Logger logger, MapleAESOFB out, MapleAESOFB in){
		this.connection = connection;
		this.logger = logger;
		cryptSend = out;
		cryptRecv = in;
		id = -1;
		gmLevel = 0;
	}
	
	public void setLoginMessage(String loginMessage) {
		this.loginMessage = loginMessage;
		try {
			MapleDatabase.getInstance().execute("UPDATE `accounts` SET `login_message`=? WHERE `id`=?", loginMessage, getId());
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void setId(int id) {
		this.id = id;
		username = accountNames.get(id);
	}
	
	public void setChannelId(int channel) {
		channelId = channel;
	}
	
	public int getChannelId() {
		return channelId;
	}
	
	public World getWorld(){
		if(worldId < 0){
			return null;
		}
		return MapleServer.getWorlds().get(worldId);
	}
	
	public MapleChannel getChannel(){
		if(worldId < 0){
			return null;
		}
		if(channelId < 0){
			return null;
		}
		
		return getWorld().getChannelById(channelId);
	}
	
	public void sendPacket(byte[] packet){

		if(packet == null){
			throw new IllegalArgumentException("packet cannot be null");
		}
		if(packet.length == 0){
			return;//We don't want to bother netty
		}
		
		/*try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
/*		ByteBuf buf = Unpooled.wrappedBuffer(packet).order(ByteOrder.LITTLE_ENDIAN);

		short s = buf.readShort();
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if(getCharacter() != null && (getCharacter().getName().equalsIgnoreCase("Duey") || getCharacter().getName().equalsIgnoreCase("FilthyMan"))){
			for(SendOpcode op : SendOpcode.values()){
				if(op == SendOpcode.MOVE_MONSTER_RESPONSE || op == SendOpcode.NPC_ACTION){
					continue;
				}
				if(op.getValue() == s){
					System.out.println(op.name()+" >> "+getCharacter().getName());
				}
			}
		}*/
		
		connectionLock.lock();
		
		try{
			connection.writeAndFlush(packet);
		}finally{
			connectionLock.unlock();
		}
		
		
	}
	
	public void changeChannel(MapleChannel channel){
		changingChannels = true;
		
		try {
			getCharacter().saveToDatabase(false);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		
		try {
			sendPacket(PacketFactory.getChannelChange(InetAddress.getByName(ServerConstants.CHANNEL_SERVER_IP), channel.getPort()));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
	}
	
	public void changeChannel(int channelId){
		
		MapleChannel mc = getWorld().getChannelById(channelId);
		
		if(mc == null){
			throw new IllegalArgumentException("No channel with id "+channelId);
		}
		
		changeChannel(mc);
	}
	
	public boolean isLoggedIn(){
		return username != null;
	}
	
	public void sendWorldList(){
		int i = 0;
		
		for(World world : MapleServer.getWorlds()){
			sendPacket(PacketFactory.getServerList(i, "World "+i, 3, world.getEventMessage(this), world));
			i++;
		}
		
		sendPacket(PacketFactory.getEndOfServerList());
	}
	
	public List<MapleCharacter> loadCharacters(int worldId){
		
		List<MapleCharacter> characters = new ArrayList<MapleCharacter>();
		
		try {
			List<QueryResult> results = MapleDatabase.getInstance().query("SELECT * FROM `characters` WHERE `owner`=? AND `world`=?", id, worldId);
			
			for(QueryResult result : results){
				
				MapleCharacter chr = new MapleCharacter(this);
				
				chr.loadFromQuery(result);
				
				characters.add(chr);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return characters;
		
	}
	
	public int login(String username, String password){
		
		try {
			List<QueryResult> results = MapleDatabase.getInstance().query("SELECT `id`,`pic`,`loggedin`,`gm`,`password`,`salt`,`login_message` FROM `accounts` WHERE `username`=?", username);
			
			if(results.size() == 0){
				return 5;//No account found
			}
			
			QueryResult first = results.get(0);
			
			String storedPassword = first.get("password");
			String salt = first.get("salt");
			
			if(AccountEncryption.hash(password, salt).equals(storedPassword)){
				this.username = username;
				this.id = first.get("id");
				accountNames.put(this.id, this.username);
				this.pic = first.get("pic");
				loginStatus = LoginStatus.byId(first.get("loggedin"));
				this.gmLevel = first.get("gm");
				this.loginMessage = first.get("login_message");
				
				return 0;
			}else{
				return 4;//Incorrect password
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			return 8;//System Error
		} catch(Exception e){
			e.printStackTrace();
			return 8;
		}
		
	}
	
	public boolean isGM(){
		return gmLevel > 0;
	}
	
	public boolean isPicCreated(){
		return pic != null && pic.length() >= 6;
	}
	
	public void registerPic(String pic){
		this.pic = pic;
		try {
			MapleDatabase.getInstance().execute("UPDATE `accounts` SET `pic`=? WHERE `id`=?", pic, id);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean accountExists(String username) throws SQLException{
		List<QueryResult> results = MapleDatabase.getInstance().query("SELECT COUNT(*) FROM `accounts` WHERE `username`=?", username);
		
		return results.get(0).getCountResult() > 0;
	}
	
	public static boolean registerAccount(String username, String password) throws SQLException {
		
		if(accountExists(username)){
			return false;
		}
		
		String salt = AccountEncryption.getRandomSalt();
		String hashedPassword = AccountEncryption.hash(password, salt);
		
		int i = MapleDatabase.getInstance().execute("INSERT INTO `accounts` (`username`, `password`, `salt`, `pic`) VALUES (?, ?, ?, ?)", username, hashedPassword, salt, "");
		
		return i > 0;
	}
	
	public boolean hasCharacterWithId(int id) throws SQLException{
		
		List<QueryResult> result = MapleDatabase.getInstance().query("SELECT `id` FROM `characters` WHERE `id`=? AND `owner`=?", id, getId());
		
		return result.size() > 0;
		
	}

	public boolean checkPic(String pic2) {
		if(pic.equals(pic2)){
			return true;
		}
		return false;
	}

	public void sendChannelAddress(InetAddress address, int port, int charid) {
		sendPacket(PacketFactory.getChannelIP(address, port, charid));
	}

	public void closeConnection() {
		connection.close();
	}

	public void sendReallowActions() {
		sendPacket(PacketFactory.getAllowActionsPacket());
	}

	public boolean deleteCharacter(int cid) throws SQLException {
		int i = MapleDatabase.getInstance().execute("DELETE FROM `characters` WHERE `id`=? AND `owner`=?", cid, id);
		
		return i > 0;
	}

	public LoginStatus getLoginStatus() {
		return loginStatus;
	}
	
	public void setLoggedInStatus(LoginStatus status){
		if(id == -1){
			logger.warn("Could not set logged in status because id is -1");
			return;
		}
		if(loginStatus != status){
			loginStatus = status;
			
			try {
				MapleDatabase.getInstance().execute("UPDATE `accounts` SET `loggedin`=? WHERE `id`=?", loginStatus.getId(), getId());
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			
			if(character != null){
				if(character.getParty() != null){
					character.getParty().updateMember(character);
				}
				if(character.getGuild() != null){
					character.getGuild().updateGuild();
				}
			}
		}
	}

	public void sendPing() {
		
		sendPacket(PacketFactory.getPing());
		
		pingTracker = System.currentTimeMillis();
		
	}
	
	public void receivePong(){
		ping = System.currentTimeMillis() - pingTracker;
	}

	
}
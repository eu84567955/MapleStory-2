

function start(){
	str = "";
	str += "Character Name: "+cm.getCharacter().getName()+"\r\n";
	str += "Ping: "+cm.getCharacter().getClient().getPing()+"\r\n";
	str += "Current Map: "+cm.getCharacter().getMap().getMapId()+"\r\n";
	str += "Player ID: "+cm.getCharacter().getId()+"\r\n";
	
	cm.sendOk(str);
	cm.dispose();
}
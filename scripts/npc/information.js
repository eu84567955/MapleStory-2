

function start(){
	str = "";
	str += "Character Name: "+cm.getCharacter().getName()+"\r\n";
	str += "Ping: "+cm.getCharacter().getClient().getPing()+"\r\n";
	cm.sendOk(str);
	cm.dispose();
}
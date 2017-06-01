package database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import central.Central;
import central.Component;
import central.ExceptionHandler;
import central.ThreadedComponent;
import ch.qos.logback.core.net.SyslogOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database extends ThreadedComponent {
	Connection databaseConnection;
	Statement statement;
	private static final Logger logger = LoggerFactory.getLogger(Database.class);

	public Database(Central central, File folder) {
		super(Component.DATABASE, central);
		// DataBase creation:
		try {
			// Save the database at the user home directory, subdirectory
			// .Flowmusic
			String dbName = "~/.FlowMusic/data";
			// or, if specified, use this instead
		if(folder != null && folder.getParentFile().exists()){
				dbName = folder.getAbsolutePath();
			}
			Class.forName("org.h2.Driver");
			databaseConnection = DriverManager.getConnection("jdbc:h2:"+dbName);
		} catch (ClassNotFoundException e) { 
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		} catch (SQLException e) {
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				ExceptionHandler.showErrorDialog(e);
				logger.error("", e);
			}
		}
	}

	@Override
	protected JSONObject onMessage(Component sender, JSONObject msg) throws Exception {
		String command = msg.getString("command");
		JSONObject ret = new JSONObject();
		String what;
		if(msg.has("what") && !msg.isNull("what")){
			what = msg.getString("what");
		}else{
			what = "";
		}
		
		switch (command) {
		
		case "start":
			addAllTables();
			ret.put("answer", "done");
			return ret;
		case "get":

			
			// TODO: further selection, filtering, joining, ...
			
			String filter="";
			if(msg.get("filter") instanceof JSONObject){//check if there is a JSONObject with a criteria
				JSONObject obj=(JSONObject) msg.get("filter");
				DBTables[] tables=DBTables.values();
				for(int i=0; i<tables.length; i++){//check in the filter for the attribute
					for(int j=0; j<tables[i].getAttributes().size();j++){
						filter=obj.optString(tables[i].getAttributes().get(j), filter);
					}
				}
			}else{
				filter = msg.get("filter").toString();
			}
			
			JSONArray found = new JSONArray();

			switch(what){
			case "song":
				switch(filter){
				case "*":
					found = getAllSongInformation();
				break;
				default:
					found = search(filter);
				break;
				}
				
				break;
			default:
				break;
			}
			
			ret.put("answer", found);
			return ret;
		case "update"://TODO
			break;
		case "insertSong":
			JSONObject newSong = new JSONObject();
			newSong.put(DBAttributes.TITLE,msg.get(DBAttributes.TITLE));
			newSong.put(DBAttributes.TYPE, msg.getJSONArray("sources").getJSONObject(0).get(DBAttributes.TYPE));
			newSong.put(DBAttributes.VALUE, msg.getJSONArray("sources").getJSONObject(0).get(DBAttributes.VALUE));
			if(msg.has(DBAttributes.ARTIST_NAME)){
				newSong.put(DBAttributes.ARTIST_NAME, msg.get(DBAttributes.ARTIST_NAME));
			}
			if(msg.has(DBAttributes.YEAR)){
				newSong.put(DBAttributes.YEAR, msg.get(DBAttributes.YEAR));
			}
			if(msg.has(DBAttributes.ALBUM_NAME)){
				newSong.put(DBAttributes.ALBUM_NAME, msg.get(DBAttributes.ALBUM_NAME));
			}
			if(msg.has(DBAttributes.TAG_NAME)){
				newSong.put(DBAttributes.TAG_NAME, msg.get(DBAttributes.TAG_NAME));
			}
			addSong(newSong);
			ret.put("answer", "done");
			return ret;
		case "delete"://TODO
			switch(what){
			case "playlist":
			case "playlistentry":
			}
			break;
			
		default:
			

		
			// TODO: error
		}
		
		return null;
	}

	private JSONArray query(String query){
		JSONArray result = new JSONArray();
		try{
			Statement stmt = databaseConnection.createStatement();
			ResultSet rs = stmt.executeQuery(query);
			ResultSetMetaData meta = rs.getMetaData();
			while(rs.next()){
				JSONObject line = new JSONObject();
				
				for(int i = 1; i <= meta.getColumnCount(); i++){
					Object obj = rs.getObject(i);
					String columnName = meta.getColumnName(i);
					if(obj instanceof String){
						line.put(columnName.toLowerCase(), (String) obj);
					}else if(obj instanceof Integer){
						line.put(columnName.toLowerCase(), (Integer) obj);
					}
				}
				result.put(line);
			}
		}catch(SQLException e){
			ExceptionHandler.showErrorDialog(e);
            logger.error("", e);
		}

		return result;
	}
	
 	private void addSong(JSONObject song){
		String insert;
		
		int songId;
		int albumId;
		int artistId;
		String year = "0";
		String type = turnToSqlString(song.getString(DBAttributes.TYPE));
		String title = turnToSqlString(song.getString(DBAttributes.TITLE));
		
		
		String value = turnToSqlString(song.getString(DBAttributes.VALUE));
		String album = "";
		String artist = "";
		String tag = "";
		ResultSet rs;
		if(song.has(DBAttributes.YEAR) && !song.isNull(DBAttributes.YEAR)){
			year = turnToSqlString(song.getString(DBAttributes.YEAR));
		}
		if(song.has(DBAttributes.ALBUM_NAME) && !song.isNull(DBAttributes.ALBUM_NAME)){
			album = turnToSqlString(song.getString(DBAttributes.ALBUM_NAME));
		}
		if(song.has(DBAttributes.ARTIST_NAME) && !song.isNull(DBAttributes.ARTIST_NAME)){
			artist = turnToSqlString(song.getString(DBAttributes.ARTIST_NAME));
		}
		if(song.has(DBAttributes.TAG_NAME) && !song.isNull(DBAttributes.TAG_NAME)){
			tag = turnToSqlString(song.getString(DBAttributes.TAG_NAME));
		}

		try {
			statement= databaseConnection.createStatement();
			
			//TODO
			//better request for song existence needed
			JSONArray information;
			insert= "SELECT "+DBAttributes.ARTIST_ID+" FROM "+DBTables.Artist+" WHERE "+DBAttributes.ARTIST_NAME+" = '"+artist+"'";
			information=query(insert);
			
			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.Artist+" ("+DBAttributes.ARTIST_NAME+")"+
						"VALUES ('"+artist+"')";
				statement.executeUpdate(insert, statement.RETURN_GENERATED_KEYS);
				rs=statement.getGeneratedKeys();
				rs.next();
				artistId=rs.getInt(1);
			}else{
				artistId = information.getJSONObject(0).getInt(DBAttributes.ARTIST_ID);
			}
			insert= "SELECT "+DBAttributes.SONG_ID+" FROM "+DBTables.Song+" WHERE "+DBAttributes.TITLE+" LIKE '"+title+"'"+ " AND "+DBAttributes.ARTIST_ID+" = "+artistId;
			information=query(insert);

			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.Song+" ("+DBAttributes.ARTIST_ID+","+DBAttributes.YEAR+","+DBAttributes.TITLE+")"+
						"VALUES ("+artistId+", "+ year+", '"+ title+"')";
				statement.executeUpdate(insert, statement.RETURN_GENERATED_KEYS);
				rs=statement.getGeneratedKeys();
				rs.next();
				songId=rs.getInt(1);
			}else{
				songId = information.getJSONObject(0).getInt(DBAttributes.SONG_ID);
			}
				
			insert= "SELECT "+DBAttributes.SOURCE_ID+" FROM "+DBTables.Source+" WHERE "+DBAttributes.SONG_ID+" LIKE "+songId;
			information=query(insert);
			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.Source+" ("+DBAttributes.SONG_ID+","+DBAttributes.TYPE+","+DBAttributes.VALUE+")"+
						"VALUES ("+songId+", '"+type+"', '"+ value+"')";
				statement.executeUpdate(insert, statement.RETURN_GENERATED_KEYS);
			}
			
			
			insert= "SELECT "+DBAttributes.ALBUM_ID+" FROM "+DBTables.Album+" WHERE "+DBAttributes.ALBUM_NAME+" LIKE '"+album+"'";
			information=query(insert);
			if(information.isNull(0)){
				if(album==""){
					insert = "INSERT INTO "+DBTables.Album+" ("+DBAttributes.ALBUM_NAME+", "+DBAttributes.ARTIST_ID+")"+
							"VALUES ('"+album+"', "+0+")";
					statement.executeUpdate(insert, statement.RETURN_GENERATED_KEYS);
					rs=statement.getGeneratedKeys();
					rs.next();
					albumId=rs.getInt(1);
				}else{
					insert = "INSERT INTO "+DBTables.Album+" ("+DBAttributes.ALBUM_NAME+", "+DBAttributes.ARTIST_ID+")"+
							"VALUES ('"+album+"', "+artistId+")";
					statement.executeUpdate(insert, statement.RETURN_GENERATED_KEYS);
					rs=statement.getGeneratedKeys();
					rs.next();
					albumId=rs.getInt(1);
				}
			}else{
				albumId = information.getJSONObject(0).getInt(DBAttributes.ALBUM_ID);
			}

			insert = "SELECT * FROM "+DBTables.Tag+" WHERE "+DBAttributes.SONG_ID+" = "+songId;
			information=query(insert);
			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.Tag+" ("+DBAttributes.TAG_NAME+","+DBAttributes.SONG_ID+")"+
						"VALUES ('"+tag+"',"+songId+")";
				statement.executeUpdate(insert);
			}
			
			insert = "SELECT * FROM "+DBTables.AlbumEntry+" WHERE "+DBAttributes.ALBUM_ID+" = "+albumId+" AND "+DBAttributes.SONG_ID+" = "+songId;
			information=query(insert);
			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.AlbumEntry+" ("+DBAttributes.ALBUM_ID+","+DBAttributes.SONG_ID+")"+
						"VALUES ("+albumId+", "+songId+")";
				statement.executeUpdate(insert);
			}
			logger.info("New information was added to tables.");
		
		} catch (SQLException e) {
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		} catch (Exception e){
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		}finally{
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				ExceptionHandler.showErrorDialog(e);
				logger.error("", e);
			}
		}
	}
	
	private void addPlaylist(String name){//TODO test
		Timestamp stamp = new Timestamp(System.currentTimeMillis());
		String insert = "INSERT INTO "+DBTables.Playlist+" ("+DBAttributes.NAME+")"+
						"VALUES ('"+name+"', "+stamp.toString()+")";
		query(insert);
	}
	
	private void addSongToPlaylist(int songId, int playlistId, int trackNumber){//TODO test
		Timestamp stamp = new Timestamp(System.currentTimeMillis());
		JSONArray information =	getSong(Integer.toString(songId));
		String insert;
		if(!information.isNull(0)){
			insert= "SELECT "+DBAttributes.PLAYLIST_ID+" FROM "+DBTables.PlaylistEntry+" WHERE "+DBAttributes.PLAYLIST_ID+" LIKE '"+playlistId+"' "+
					"AND "+DBAttributes.SONG_ID+" = "+songId+
					"AND "+DBAttributes.NR+" = "+trackNumber;
			information=query(insert);
			if(information.isNull(0)){
				insert = "INSERT INTO "+DBTables.PlaylistEntry+" ("+DBAttributes.PLAYLIST_ID+", "+DBAttributes.SONG_ID+", "+DBAttributes.NR+")"+
						"VALUES ('"+playlistId+"', "+songId+", "+trackNumber+")";
				query(insert);
				insert = "UPDATE "+DBTables.Playlist+" SET "+DBAttributes.TIMESTAMP+" = "+stamp.toString()+" WHERE "+DBAttributes.PLAYLIST_ID+" = "+playlistId;
				logger.info("Song added to playlist.");
			}else{
				logger.error("Tracknumber in playlist already taken.");
			}
		}else{
			logger.error("Song not found.");
		}
	}
	
	private void removeSong(int ID){//TODO
		
	}
	
	private JSONArray search(String search){//TODO
		String get = "SELECT "+DBTables.Song+".*, "+DBTables.Artist+"."+DBAttributes.ARTIST_NAME+", "+DBTables.Album+"."+DBAttributes.ALBUM_NAME+
				" FROM "+DBTables.Song+", "+DBTables.Artist+","+DBTables.Album+
				" WHERE "+DBTables.Artist+"."+DBAttributes.ARTIST_ID+" = "+DBTables.Song+"."+DBAttributes.ARTIST_ID+
				" AND (LOWER("+DBTables.Song+"."+DBAttributes.TITLE+") LIKE '%"+search.toLowerCase()+"%' OR LOWER("+DBTables.Artist+"."+DBAttributes.ARTIST_NAME+") LIKE '%"+search.toLowerCase()+"%' OR LOWER("+DBTables.Album+"."+DBAttributes.ALBUM_NAME+") LIKE '%"+search.toLowerCase()+"%' )"+
				" AND "+DBTables.Album+"."+DBAttributes.ALBUM_ID+" = (SELECT "+DBAttributes.ALBUM_ID+" FROM "+DBTables.AlbumEntry+" WHERE "+DBTables.AlbumEntry+"."+DBAttributes.SONG_ID+" = "+DBTables.Song+"."+DBAttributes.SONG_ID+")";
		
		
		return getAllInfo(get);
	}
	
	private JSONArray getAllSongInformation(){
		String get = "SELECT "+DBTables.Song+".*, "+DBTables.Artist+"."+DBAttributes.ARTIST_NAME+
				" FROM "+DBTables.Song+", "+DBTables.Artist+
				" WHERE "+DBTables.Artist+"."+DBAttributes.ARTIST_ID+" = "+DBTables.Song+"."+DBAttributes.ARTIST_ID;
		return getAllInfo(get);
	}
	
	private JSONArray getAllInfo(String get){//collects all info of song selection and returns it 
		JSONArray songInfo=query(get);
		JSONArray tags;
		JSONArray albums;
		JSONObject obj;
		String id;
		for(int i=0; i<songInfo.length(); i++){
			if(songInfo.get(i) instanceof JSONObject){
				obj = songInfo.getJSONObject(i);
				id = obj.get(DBAttributes.SONG_ID).toString();
				
				get= "SELECT * FROM "+DBTables.Source+" WHERE "+DBAttributes.SONG_ID+" = "+id;
				songInfo.getJSONObject(i).put("sources", query(get));

				get= "SELECT "+DBAttributes.TAG_NAME+" FROM "+DBTables.Tag+" WHERE "+DBAttributes.SONG_ID+" = "+id;
				tags=query(get);
				for(int j=0; j<tags.length();j++){
					tags.put(j, tags.getJSONObject(j).getString(DBAttributes.TAG_NAME));
				}
				songInfo.getJSONObject(i).put(DBTables.Tag.toString().toLowerCase(), tags);
				
				get= "SELECT "+DBAttributes.ALBUM_NAME+" FROM "+DBTables.Album+", "+DBTables.AlbumEntry+" WHERE "+DBTables.Album+"."+DBAttributes.ALBUM_ID+" = "+DBTables.AlbumEntry+"."+DBAttributes.ALBUM_ID+" AND "+DBTables.AlbumEntry+"."+DBAttributes.SONG_ID+" = "+id;
				songInfo.getJSONObject(i).put(DBAttributes.ALBUM_NAME, query(get));
				albums=query(get);
				for(int j=0; j<tags.length();j++){
					albums.put(j, albums.getJSONObject(j).getString(DBAttributes.ALBUM_NAME));
				}
				songInfo.getJSONObject(i).put(DBAttributes.ALBUM_NAME, albums);
			}
		}
		return songInfo;
	}
	
	private JSONArray getSong(String ID){//TODO
		String get = "SELECT "+".*" +
				" FROM "+DBTables.Song+", "+DBTables.Artist+", "+DBTables.Source+","+DBTables.Album+
				" WHERE "+DBTables.Song+"."+DBAttributes.SONG_ID+" = "+ID+
				" AND "+DBTables.Artist+"."+DBAttributes.ARTIST_ID+" = "+DBTables.Song+"."+DBAttributes.ARTIST_ID+
				" AND "+DBTables.Source+"."+DBAttributes.SONG_ID+" = "+DBTables.Song+"."+DBAttributes.SONG_ID+
				" AND "+DBTables.Album+"."+DBAttributes.ALBUM_ID+" = (SELECT "+DBAttributes.ALBUM_ID+" FROM "+DBTables.AlbumEntry+" WHERE "+DBAttributes.SONG_ID+" = "+"'"+ID+"')";
		JSONArray x=query(get);
		x.forEach(e->System.out.println(e.toString()));
		return x;
	}
	
	private void addAllTables(){
		try {
			String table = "CREATE TABLE "+DBTables.Playlist + 
							"( "+DBAttributes.PLAYLIST_ID+" int NOT NULL AUTO_INCREMENT, "+
							DBAttributes.NAME+" varchar(255) NOT NULL, " + 
							DBAttributes.TIMESTAMP+" varchar(255), "+
							"PRIMARY KEY("+DBTables.PLAYLIST_ID+"))";
			statement= databaseConnection.createStatement();
			statement.executeUpdate(table);
			
			
			
			table = "CREATE TABLE "+DBTables.PlaylistEntry+
					"( "+DBAttributes.PLAYLIST_ID+" int NOT NULL AUTO_INCREMENT, "+
					DBAttributes.SONG_ID+" int NOT NULL, "+
					DBAttributes.NR+" int(4), "+
					"PRIMARY KEY ("+DBAttributes.PLAYLIST_ID+", "+DBAttributes.SONG_ID+"))";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.Artist+
					"( "+DBAttributes.ARTIST_ID+" int NOT NULL AUTO_INCREMENT, "+
					DBAttributes.ARTIST_NAME+" varchar(255), "+
					"PRIMARY KEY ("+DBAttributes.ARTIST_ID+"))";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.Song +
					"( "+DBAttributes.SONG_ID+" int NOT NULL AUTO_INCREMENT, "+
					DBAttributes.ARTIST_ID+" int NOT NULL, "+
					DBAttributes.YEAR+" int(4), "+
					DBAttributes.TITLE+" varchar(255), "+
					"PRIMARY KEY ("+DBAttributes.SONG_ID+"))";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.Album+
					"("+DBAttributes.ALBUM_ID+" int NOT NULL AUTO_INCREMENT, "+
					DBTables.ALBUM_NAME+" varchar(255), "+
					DBTables.ARTIST_ID+" int NOT NULL ,"+
					"PRIMARY KEY ("+DBAttributes.ALBUM_ID+"))";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.AlbumEntry+
					"("+DBAttributes.ALBUM_ID+" int NOT NULL, "+
					DBAttributes.SONG_ID+" int NOT NULL)";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.Source+
					"("+DBAttributes.SOURCE_ID+" int NOT NULL AUTO_INCREMENT, "+
					DBAttributes.SONG_ID+" int NOT NULL, "+
					DBAttributes.TYPE+" varchar(255), "+
					DBAttributes.VALUE+" varchar(255), "+
					"PRIMARY KEY ("+DBAttributes.SOURCE_ID+"))";
			statement.executeUpdate(table);
			
			table = "CREATE TABLE "+DBTables.Tag+
					"("+DBAttributes.TAG_NAME+" varchar(255), "+
					DBAttributes.SONG_ID+" int NOT NULL)";
			statement.executeUpdate(table);
			logger.info("All tables sucessfully created...");
		} catch (SQLException e) {
			logger.info("Tables found...");
		}catch(Exception e){
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		}finally{
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				ExceptionHandler.showErrorDialog(e);
				logger.error("", e);
			}
		}
	}
	
	private void dropAllTables(){
		String drop;
		try {
			drop="DROP TABLE "+DBTables.Playlist;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.PlaylistEntry;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.Artist;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.Song;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.Album;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.AlbumEntry;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.Source;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			
			drop="DROP TABLE "+DBTables.Tag;
			statement= databaseConnection.createStatement();
			statement.executeUpdate(drop);
			logger.info("All tables droped...");
		} catch (SQLException e) {
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		} catch (Exception e){
			ExceptionHandler.showErrorDialog(e);
			logger.error("", e);
		} finally{
			try {
				if (statement != null) {
					statement.close();
				}
			} catch (SQLException e) {
				ExceptionHandler.showErrorDialog(e);
				logger.error("", e);
			}
		}
	}
	
	private String turnToSqlString(String string){//transforms string for sqlstatement when apostroph is used in string
		return string.replaceAll("'", "''");
	}
	

}

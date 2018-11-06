package net.b07z.sepia.server.assist.parameters;

import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import net.b07z.sepia.server.assist.assistant.LANGUAGES;
import net.b07z.sepia.server.assist.interpreters.NluInput;
import net.b07z.sepia.server.assist.interpreters.NluResult;
import net.b07z.sepia.server.assist.interpreters.NluTools;
import net.b07z.sepia.server.assist.interpreters.NormalizerAddStrongDE;
import net.b07z.sepia.server.assist.interpreters.NormalizerAddStrongEN;
import net.b07z.sepia.server.assist.interpreters.Normalizer;
import net.b07z.sepia.server.assist.interviews.InterviewData;
import net.b07z.sepia.server.assist.server.Config;
import net.b07z.sepia.server.assist.users.User;
import net.b07z.sepia.server.core.assistant.PARAMETERS;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.Is;
import net.b07z.sepia.server.core.tools.JSON;

public class RadioStation implements ParameterHandler{
	
	//------data-----
	public static JSONObject radioStationsMap = new JSONObject();
	public static JSONObject radioStationsPlaylist = new JSONObject();
	public static JSONObject radioStationCollections = new JSONObject(); 	//arrays of station-keys for e.g. a genre
	static {
		String propFile = Config.servicePropertiesFolder + "radio-stations.json";
		JSONObject radioStations = JSON.readJsonFromFile(propFile);
		try{
			radioStationsMap = JSON.getJObject(radioStations, "stations");
			radioStationsPlaylist = JSON.getJObject(radioStations, "playlists");
			radioStationCollections = JSON.getJObject(radioStations, "collections");
			if (radioStationsMap == null || radioStationsPlaylist == null){
				throw new RuntimeException("RadioStation properties file missing or broken! "
						+ "File: " + propFile + " - Error: 'stations' or 'playlists' was null!");
			}
			//rebuild linked stations - should fail if something has wrong format
			replaceStationReferences();
			
			Debugger.println("Parameters:RadioStation - Loaded " + radioStationsMap.keySet().size() 
							+ " station arrays with " + radioStationsPlaylist.keySet().size() + " playlists"
							+ " and " + radioStationCollections.keySet().size() + " collections"
							+ " from: " + propFile, 3);
		}catch(Exception e){
			Debugger.println("Parameters:RadioStation - Error during initialization: " + e.getMessage(), 1);
			throw new RuntimeException("RadioStation properties file missing or broken! "
					+ "File: " + propFile + " - Error: " + e.getMessage());
		}
	}
	//Method to replace station references with station objects (e.g. ego_fm_pure -> {"name":"egoFM Pure", ...})
	@SuppressWarnings("unchecked")
	private static void replaceStationReferences(){
		radioStationsMap.forEach((key, obj) -> {
			if (obj != null){
				//System.out.println(key);
				JSONArray stationArray = (JSONArray) obj;
				for (int i=0; i<stationArray.size(); i++){
			    	Object stationObj = stationArray.get(i);
			    	if (stationObj.getClass().equals(String.class)){
			    		//System.out.println("Station ref.: " + stationObj); 			//DEBUG
			    		Object stationData = JSON.getJArray(radioStationsMap, (String) stationObj).get(0);
			    		stationArray.set(i, stationData);
			    	}
			    }
			}
		});
	}
	/**
	 * Get one or more stations matching the station-search. 
	 * @param search - usually the normalized station name
	 */
	public static JSONArray getStation(String search){
		JSONArray entry = JSON.getJArray(radioStationsMap, search);
		//TODO: we could add some fancy fuzzy search here too ...
		return entry;
	}
	/**
	 * Put new stations array into map.
	 */
	public static void putStation(String key, JSONArray entry){
		JSON.put(radioStationsMap, key, entry);
	}
	/**
	 * Try to get a playlist link to the given station name. 
	 * @param stationName - 'name' entry of station object
	 */
	public static String getPlaylistUrl(String stationName){
		String entry = JSON.getString(radioStationsPlaylist, stationName);
		return entry;
	}
	/**
	 * Get stations array to a given genre by loading the genre array (with station links) and the first station
	 * result of each link. 
	 * @param search - normalized genre name
	 * @return entries or empty array
	 */
	public static JSONArray getGenreCollection(String search){
		JSONArray genreStations = JSON.getJArray(radioStationCollections, search);
		if (genreStations != null){
			JSONArray genreStationFirstEntries = new JSONArray();
			for (Object ao : genreStations){
				String normalizedStationName = (String) ao;
				JSONArray entry = JSON.getJArray(radioStationsMap, normalizedStationName);
				if (Is.notNullOrEmpty(entry)){
					JSON.add(genreStationFirstEntries, entry.get(0));
				}
			}
			return genreStationFirstEntries;
		}else{
			return new JSONArray();
		}
		//TODO: we could add some fancy fuzzy search here too ...
	}
	
	/*private static JSONArray makeStationArray(JSONObject... stations){
		JSONArray ja = new JSONArray();
		for (JSONObject station : stations){
			JSON.add(ja,station);
		}
		return ja;
	}*/
	
	//--------

	User user;
	String language;
	boolean buildSuccess = false;
	NluInput nluInput;
	
	String found;
	
	@Override
	public void setup(NluInput nluInput) {
		this.user = nluInput.user;
		this.language = nluInput.language;
		this.nluInput = nluInput;
	}
	@Override
	public void setup(NluResult nluResult) {
		this.user = nluResult.input.user;
		this.language = nluResult.language;
		this.nluInput = nluResult.input;
	}
	
	@Override
	public String extract(String input) {
		String station = "";
		
		//check storage first
		ParameterResult pr = nluInput.getStoredParameterResult(PARAMETERS.RADIO_STATION);
		if (pr != null){
			station = pr.getExtracted();
			this.found = pr.getFound();
			
			return station;
		}
		
		//-----------------
		//get genre first if present
		ParameterResult prGenre = ParameterResult.getResult(nluInput, PARAMETERS.MUSIC_GENRE, input);
		String genre = prGenre.getExtracted();
		/*
		//one could try and remove it
		if (!genre.isEmpty()){
			input = ParameterResult.cleanInputOfFoundParameter(nluInput, PARAMETERS.MUSIC_GENRE, prGenre, input);
		}
		*/
		
		//German
		if (language.matches("de")){
			String radio = NluTools.stringFindFirst(input, "radiokanal|radio kanal|radiostation|radio station|radiosender|radio sender|radiostream|radio stream|"
														+ "musikstation|musik station|musiksender|musik sender|kanal|sender|radio|station");
			String action1 = NluTools.stringFindFirst(input, "einschalten|anmachen|oeffnen|starten|an|ein|hoeren|spielen|aktivieren|aufdrehen");
			String action2 = NluTools.stringFindFirst(input, "oeffne|starte|spiel|spiele|aktiviere");
			//v1
			if (!radio.isEmpty() && !action1.isEmpty()){
				station = NluTools.stringFindFirst(input, radio + "\\s(.*?\\s.*?|.*?)\\s" + action1);
				station = station.replaceFirst(".*?\\b" + radio + "\\s", "").trim();
				station = station.replaceFirst("\\s" + action1 + "\\b.*", "").trim();
			}
			//v2
			if (station.trim().isEmpty() && !radio.isEmpty() && !action1.isEmpty()){
				station = NluTools.stringFindFirst(input, "\\b(.*?\\s.*?|.*?)\\s" + radio + "\\s" + action1);
				station = station.replaceFirst("\\s" + radio + "\\b.*", "").trim();
			}
			//v3
			if (station.trim().isEmpty() && !radio.isEmpty() && !action2.isEmpty()){
				station = NluTools.stringFindFirst(input, action2 + "\\s" + radio + "\\s(.*?\\s\\w+|\\w+)$");
				station = station.replaceFirst(".*?\\s" + radio + "\\s", "").trim();
			}
			//v4
			if (station.trim().isEmpty() && !radio.isEmpty() && !action2.isEmpty()){
				station = NluTools.stringFindFirst(input, action2 + "\\s" + "(.*?)\\s" + radio + "$");
				station = station.replaceFirst(".*?\\b" + action2 + "\\s", "").trim();
				station = station.replaceFirst("\\s" + radio, "").trim();
			}
			//v5
			if (station.trim().isEmpty() && !radio.isEmpty() && action1.isEmpty() && action2.isEmpty()){
				String possibleStation = NluTools.stringRemoveFirst(input, radio);
				if (NluTools.countWords(possibleStation) <= 3){
					station = possibleStation;
				}
			}
			
			//optimize
			if (!station.trim().isEmpty()){
				Normalizer normalizer = new NormalizerAddStrongDE();
				station = normalizer.normalizeText(station);
				station = station.replaceFirst(".*?\\b(einen|ein|eine|die|den|das)\\b", "").trim();
				//is just genre?
				if (station.equals(genre)){
					station = "";
				}
			}
		
		//English
		}else if (language.matches("en")){
			String radio = NluTools.stringFindFirst(input, "radiochannel|radiostation|radio station|radio channel|radiostream|radio stream|"
														+ "music station|music channel|channel|sender|radio|station");
			String action1 = NluTools.stringFindFirst(input, "on");
			String action2 = NluTools.stringFindFirst(input, "open|start|play|activate|tune in to|turn on|switch on");
			//v1
			if (!radio.isEmpty() && !action1.isEmpty()){
				station = NluTools.stringFindFirst(input, radio + "\\s(.*?\\s.*?|.*?)\\s" + action1);
				station = station.replaceFirst(".*?\\b" + radio + "\\s", "").trim();
				station = station.replaceFirst("\\s" + action1 + "\\b.*", "").trim();
			}
			//v2
			if (station.trim().isEmpty() && !radio.isEmpty() && !action1.isEmpty()){
				station = NluTools.stringFindFirst(input, "\\b(.*?\\s.*?|.*?)\\s" + radio + "\\s" + action1);
				station = station.replaceFirst("\\s" + radio + "\\b.*", "").trim();
			}
			//v3
			if (station.trim().isEmpty() && !radio.isEmpty() && !action2.isEmpty()){
				station = NluTools.stringFindFirst(input, action2 + "\\s" + radio + "\\s(.*?\\s\\w+|\\w+)$");
				station = station.replaceFirst(".*?\\s" + radio + "\\s", "").trim();
			}
			//v4
			if (station.trim().isEmpty() && !radio.isEmpty() && !action2.isEmpty()){
				station = NluTools.stringFindFirst(input, action2 + "\\s" + "(.*?)\\s" + radio + "$");
				station = station.replaceFirst(".*?\\b" + action2 + "\\s", "").trim();
				station = station.replaceFirst("\\s" + radio, "").trim();
			}
			//v5
			if (station.trim().isEmpty() && !radio.isEmpty() && action1.isEmpty() && action2.isEmpty()){
				String possibleStation = NluTools.stringRemoveFirst(input, radio);
				if (NluTools.countWords(possibleStation) <= 3){
					station = possibleStation;
				}
			}
			
			//optimize
			if (!station.trim().isEmpty()){
				Normalizer normalizer = new NormalizerAddStrongEN();
				station = normalizer.normalizeText(station);
				station = station.replaceFirst(".*?\\b(a|an|the|to)\\b", "").trim();
				//is just genre?
				if (station.equals(genre)){
					station = "";
				}
			}
		
		//no result/missing language support ...
		}else{
			//leave it empty
		}
		//System.out.println("Final station: " + station); 		//debug
		//-----------------
		
		this.found = station;
		
		//reconstruct original phrase to get proper item names
		if (!station.isEmpty()){
			Normalizer normalizer = Config.inputNormalizers.get(language);
			station = normalizer.reconstructPhrase(nluInput.textRaw, station);
		}
		
		//store it
		pr = new ParameterResult(PARAMETERS.RADIO_STATION, station, found);
		nluInput.addToParameterResultStorage(pr);
				
		return station;
	}
	
	@Override
	public String guess(String input) {
		return "";
	}

	@Override
	public String getFound() {
		return found;
	}

	@Override
	public String remove(String input, String found) {
		return NluTools.stringRemoveFirst(input, Pattern.quote(found));
	}
	
	@Override
	public String responseTweaker(String input){
		if (language.equals(LANGUAGES.DE)){
			input = input.replaceAll("(?i).*\\b(starte|spiele|oeffne)\\b", "").trim();
			return input.replaceAll("(?i)\\b(starten|spielen|oeffnen|hoeren)\\b", "").trim();
		}else{
			input = input.replaceAll("(?i).*\\b(start|play|open|listen to)\\b", "").trim();
			return input;
		}
	}

	@Override
	public String build(String input) {
		//redirect
		/*
		station = station.replaceFirst("(?i)\\b(bundesliga|fussball|sport)\\b", "sport1");
		*/
		//string to search in cache
		String station = input;
		station = normalizeStationName(station);

		//build default result
		JSONObject itemResultJSON = new JSONObject();
			JSON.add(itemResultJSON, InterviewData.INPUT, input);
			JSON.add(itemResultJSON, InterviewData.VALUE, station);
			JSON.add(itemResultJSON, InterviewData.CACHE_ENTRY, (radioStationsMap.containsKey(station))? station : "");
			//TODO: we should make a real "search" inside the stations map via name parameter and fuzzy matching
		
		buildSuccess = true;
		return itemResultJSON.toJSONString();
	}
	/**
	 * Convert a station name to normalized format (e.g. egoFM -> ego_fm, Wdr 1 -> wdr1).
	 */
	public static String normalizeStationName(String stationIn){
		//optimize stations
		stationIn = stationIn.replaceFirst("(?i)\\b((eins|1)( live|live|life))\\b", "1live");
		stationIn = stationIn.replaceFirst("(?i)\\b(sport eins|sport 1|sport1)\\b", "sport1");
		//clean
		return stationIn.toLowerCase().replaceFirst("^(radio)\\w*|\\b(radio)\\w*$", "").replaceAll("fm\\b", "_fm")
				.replaceAll("\\s+(\\d+)\\b", "$1").replaceAll("(\\s+|-|\\.)", "_").replaceAll("__", "_").trim();
	}

	@Override
	public boolean validate(String input) {
		if (input.matches("^\\{\".*\":.+\\}$") && input.contains("\"" + InterviewData.VALUE + "\"")){
			//System.out.println("IS VALID: " + input); 		//debug
			return true;
		}else{
			return false;
		}
	}
	
	@Override
	public boolean buildSuccess() {
		return buildSuccess;
	}
	
}

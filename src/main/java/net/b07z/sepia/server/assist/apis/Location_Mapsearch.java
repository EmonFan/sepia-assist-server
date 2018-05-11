package net.b07z.sepia.server.assist.apis;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.json.simple.JSONObject;

import net.b07z.sepia.server.assist.answers.AnswerStatics;
import net.b07z.sepia.server.assist.apis.ApiInfo.Content;
import net.b07z.sepia.server.assist.apis.ApiInfo.Type;
import net.b07z.sepia.server.assist.assistant.LANGUAGES;
import net.b07z.sepia.server.assist.assistant.LOCATION;
import net.b07z.sepia.server.assist.data.Card;
import net.b07z.sepia.server.assist.data.Card.ElementType;
import net.b07z.sepia.server.assist.interpreters.NluResult;
import net.b07z.sepia.server.assist.interpreters.Normalizer;
import net.b07z.sepia.server.assist.interviews.AskClient;
import net.b07z.sepia.server.assist.interviews.NoResult;
import net.b07z.sepia.server.assist.server.Config;
import net.b07z.sepia.server.assist.server.ConfigServices;
import net.b07z.sepia.server.core.assistant.CLIENTS;
import net.b07z.sepia.server.core.assistant.PARAMETERS;
import net.b07z.sepia.server.core.tools.Converters;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.JSON;

/**
 * Location searching via Maps.
 * 
 * @author Florian Quirin
 *
 */
public class Location_Mapsearch implements ApiInterface{
	
	//-----data-----
	
	//get text for "open maps" button
	public static String getButtonText(String language){
		if (language.equals(LANGUAGES.DE)){
			return "Maps öffnen";
		}else{
			return "Open maps";
		}
	}
	public static String getButtonTextApple(String language){
		if (language.equals(LANGUAGES.DE)){
			return "Apple Maps";
		}else{
			return "Apple Maps";
		}
	}
	
	//--------------
	
	//info
	public ApiInfo getInfo(String language){
		return new ApiInfo(Type.link, Content.redirect, true);
	}

	//result
	public ApiResult getResult(NluResult NLU_result){
		//initialize result
		API api = new API(NLU_result);
		
		//get parameters
		String place = NLU_result.getParameter(PARAMETERS.SEARCH); 		//TODO: change to PLACE?
		String poi = NLU_result.getParameter(PARAMETERS.POI);
		String search;
		
		Debugger.println("cmd: location, place: " + place + ", poi: " + poi, 2);		//debug
		
		String[] user_spec_location;
		String end_param;
		if (poi.isEmpty() && !place.isEmpty()){
			search = place;
			user_spec_location = LOCATION.getUserSpecificLocation_4_Maps(NLU_result.input.user, search);
			end_param = user_spec_location[0];
		}else if (place.isEmpty() && !poi.isEmpty()){
			search = poi;
			user_spec_location = LOCATION.getUserSpecificLocation_4_Maps(NLU_result.input.user, ""); //just fake
			end_param = "";
		}else if (!place.isEmpty() && !poi.isEmpty()){
			search = place; 	//temporary
			user_spec_location = LOCATION.getUserSpecificLocation_4_Maps_with_POI(NLU_result.input.user, place);
			end_param = user_spec_location[0];
		}else{
			search = "";
			user_spec_location = LOCATION.getUserSpecificLocation_4_Maps(NLU_result.input.user, ""); //just fake
			end_param = "";
		}
		
		String end = search;
		
		//reconstruct original phrase to get proper item names
		Normalizer normalizer = Config.inputNormalizers.get(api.language);
		String end_to_say = normalizer.reconstructPhrase(NLU_result.input.text_raw, end);
		
		//check place for personal locations
		if (!end_param.isEmpty()){
			end = user_spec_location[1];
			end_to_say = user_spec_location[2];
			place = end_to_say; 		//overwrite to get rid of tag - mainly for card
			//still empty? then say that and maybe ask the user to add the info to the account
			if (end.isEmpty() || end_to_say.isEmpty()){
				if (LOCATION.canBeAddedToAccount(end_param)){
					//return No_Result.get(NLU_result, "default_miss_info_0b");
					NLU_result.setParameter(PARAMETERS.TYPE, "addresses");
					NLU_result.setParameter(PARAMETERS.ACTION, "add");
					return ConfigServices.dashboard.getResult(NLU_result);
				}else{
					return NoResult.get(NLU_result, "default_miss_info_0a");
				}
			}
		}
		
		//check again
		if (end.isEmpty() && poi.isEmpty()){
			return AskClient.question("location_ask_0a", "search", NLU_result);
		}else{
			//check for individual locations
			/*
			String personal_end = NLU_result.input.user. ... check user favorites ...;
			if (!personal_end.isEmpty()){
				end = personal_end;
				end_to_say = personal_end;
				place = end_to_say; 		//overwrite again - mainly for card
			}
			*/
		}
		
		//check POI again and add map-specific search term combination
		String endSimple = end;
		if (!poi.isEmpty() && !place.isEmpty()){
			String close_to = " " + AnswerStatics.get(AnswerStatics.CLOSE_TO, api.language) + " "; 		//language specific "close to" phrase for maps
			end = poi + close_to + end;
			endSimple = poi + " " + end;
			end_to_say = poi + close_to + end_to_say;
		}
		
		//get answer
		api.answer = Config.answers.getAnswer(NLU_result, "location_1a", end_to_say);
		api.answerClean = Converters.removeHTML(api.answer);
		
		//make action: browser url call
		String googleMapsURL = "";
		String appleMapsURL = "";
		try {
			//iOS Apple Maps
			if (CLIENTS.isAppleCompatible(NLU_result.input.client_info)){
				appleMapsURL = "http://maps.apple.com/?" +
						"q=" + URLEncoder.encode(endSimple, "UTF-8");
			}
			//Google Maps
			googleMapsURL = "https://www.google.de/maps/search/" + URLEncoder.encode(end, "UTF-8");
			
		} catch (UnsupportedEncodingException e) {
			googleMapsURL = "https://maps.google.com/";
			Debugger.println("Location_Mapsearch - failed to encode URL with: " + end, 1);
			//e.printStackTrace();
		}
		/*
		api.actionInfo_add_action(ACTIONS.OPEN_URL);
		api.actionInfo_put_info("url", googleMapsURL);
		
		//google button
		api.actionInfo_add_action(ACTIONS.BUTTON_URL);
		api.actionInfo_put_info("url", googleMapsURL);
		api.actionInfo_put_info("title", getButtonText(api.language));
		//apple button
		if (!appleMapsURL.isEmpty()){
			api.actionInfo_add_action(ACTIONS.BUTTON_URL);
			api.actionInfo_put_info("url", appleMapsURL);
			api.actionInfo_put_info("title", getButtonTextApple(api.language));
			api.hasAction = true;
		}
		api.hasAction = true;
		*/
		
		//build cards
		
		String description = end_to_say;
		
		//google
		Card googleCard = new Card(Card.TYPE_SINGLE);
		JSONObject linkCard1 = googleCard.addElement(ElementType.link, 
				JSON.make("title", "Google Maps", "desc", description),
				null, null, "", 
				googleMapsURL, 
				Config.urlWebImages + "/brands/google-maps.png", 
				null, null);
		JSON.put(linkCard1, "imageBackground", "transparent");	//use any CSS background option you wish
		api.addCard(googleCard.getJSON());
		
		//apple
		if (!appleMapsURL.isEmpty()){
			Card appleCard = new Card(Card.TYPE_SINGLE);
			JSONObject linkCard2 = appleCard.addElement(ElementType.link, 
					JSON.make("title", "Apple Maps", "desc", description),
					null, null, "", 
					appleMapsURL, 
					Config.urlWebImages + "/brands/apple-maps.png", 
					null, null);
			JSON.put(linkCard2, "imageBackground", "transparent");	//use any CSS background option you wish
			api.addCard(appleCard.getJSON());
		}
		
		//build html
		api.hasInfo = false;	
		
		api.status = "success";		//kind of success ^^
				
		//finally build the API_Result
		ApiResult result = api.buildApiResult();
				
		//return result_JSON.toJSONString();
		return result;
	}

}

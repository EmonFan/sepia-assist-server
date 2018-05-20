package net.b07z.sepia.server.assist.services.uc;

import net.b07z.sepia.server.assist.assistant.LOCATION;
import net.b07z.sepia.server.assist.data.Card;
import net.b07z.sepia.server.assist.data.Parameter;
import net.b07z.sepia.server.assist.data.Card.ElementType;
import net.b07z.sepia.server.assist.interpreters.NluResult;
import net.b07z.sepia.server.assist.interviews.InterviewData;
import net.b07z.sepia.server.assist.server.Config;
import net.b07z.sepia.server.assist.services.ServiceBuilder;
import net.b07z.sepia.server.assist.services.ServiceInfo;
import net.b07z.sepia.server.assist.services.ServiceInterface;
import net.b07z.sepia.server.assist.services.ServiceResult;
import net.b07z.sepia.server.assist.services.ServiceInfo.Content;
import net.b07z.sepia.server.assist.services.ServiceInfo.Type;
import net.b07z.sepia.server.core.assistant.PARAMETERS;
import net.b07z.sepia.server.core.tools.Debugger;
import net.b07z.sepia.server.core.tools.JSON;
import net.b07z.sepia.server.core.tools.URLBuilder;

import org.json.simple.JSONObject;

/**
 * Food delivery.
 * 
 * @author Florian Quirin
 *
 */
public class FoodDeliveryUrl implements ServiceInterface{
	
	//info
	public ServiceInfo getInfo(String language){
		//type
		ServiceInfo info = new ServiceInfo(Type.link, Content.redirect, false);
		
		//Parameters:
		//optional
		Parameter p1 = new Parameter(PARAMETERS.FOOD_ITEM, "");
		info.addParameter(p1);
				
		//Answers:
		info.addSuccessAnswer("food_1a")				//this one should work for any case
			.addFailAnswer("food_0a")		//for some reason the service failed
			.addAnswerParameters("food");
		
		return info;
	}

	//result
	public ServiceResult getResult(NluResult NLU_result){
		//initialize result
		ServiceBuilder api = new ServiceBuilder(NLU_result, getInfo(""));
		
		//get interview parameters
		Parameter p = NLU_result.getOptionalParameter(PARAMETERS.FOOD_ITEM, "");
		JSONObject itemJSON = (p.isDataEmpty())? null : p.getData();
		
		//get location
		String city = NLU_result.input.user.getCurrentLocation(LOCATION.CITY);
		String zip = NLU_result.input.user.getCurrentLocation(LOCATION.POSTAL_CODE);
		//String street = NLU_result.input.user.getCurrentLocation(LOCATION.STREET);
		//String nbr = NLU_result.input.user.getCurrentLocation(LOCATION.STREET_NBR);
		String lat = NLU_result.input.user.getCurrentLocation(LOCATION.LAT);
		String lng = NLU_result.input.user.getCurrentLocation(LOCATION.LNG);
				
		//parameter adaptation to service format
		String foodItem = "";
		if (itemJSON != null){
			foodItem = itemJSON.get(InterviewData.VALUE).toString();
		}
		api.resultInfoPut("food", foodItem);
		
		Debugger.println("cmd: food_delivery, item: " + foodItem, 2);		//debug
		
		//GET DATA
			
		//build card
		Card card1 = new Card(Card.TYPE_SINGLE);
		Card card2 = new Card(Card.TYPE_SINGLE);
		
		//TODO: improve links!
		
		//foodora
		//https://www.foodora.de/restaurants/lat/51.45939139999999/lng/7.079359299999965/plz/45307/city/Essen/address/A/A/A
		String foodoraURL = "https://www.foodora.de";
		if (!city.isEmpty() && !lat.isEmpty() && !lng.isEmpty() && !zip.isEmpty()){
			foodoraURL = URLBuilder.getString("https://www.foodora.de/restaurants",
							"/lat/", lat, "/lng/", lng,
							"/plz/", zip,
							"/city/", city) 
					+ "/address/A/A/A";
		}
		JSONObject linkCard1 = card1.addElement(ElementType.link, 
				JSON.make("title", "foodora",	"desc", "Foodora food delivery"),
				null, null, "", 
				foodoraURL, 
				Config.urlWebImages + "brands/logo-foodora.png", 
				null, null);
		JSON.put(linkCard1, "imageBackground", "transparent");	//use any CSS background option you wish
		
		//deliveroo
		String deliverooURL = "https://www.deliveroo.de";
		JSONObject linkCard2 = card2.addElement(ElementType.link, 
				JSON.make("title", "deliveroo",	"desc", "Deliveroo food delivery"),
				null, null, "", 
				deliverooURL, 
				Config.urlWebImages + "brands/logo-deliveroo.png", 
				null, null);
		JSON.put(linkCard2, "imageBackground", "transparent");	//use any CSS background option you wish
		
		//add it
		api.addCard(card1.getJSON()); 	//pre-Alpha: api.cardInfo = card.cardInfo;
		api.addCard(card2.getJSON()); 	//pre-Alpha: api.cardInfo = card.cardInfo;
		api.hasCard = true;
		
		//all clear?
		api.status = "success";
		
		//finally build the API_Result
		ServiceResult result = api.buildResult();
				
		//return result_JSON.toJSONString();
		return result;
	}

}

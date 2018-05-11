package net.b07z.sepia.server.assist.apis;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import net.b07z.sepia.server.assist.apis.ApiInfo.Content;
import net.b07z.sepia.server.assist.apis.ApiInfo.Type;
import net.b07z.sepia.server.assist.assistant.LANGUAGES;
import net.b07z.sepia.server.assist.interpreters.NluResult;
import net.b07z.sepia.server.assist.interviews.AskClient;
import net.b07z.sepia.server.assist.server.Config;
import net.b07z.sepia.server.core.assistant.ACTIONS;
import net.b07z.sepia.server.core.assistant.CLIENTS;
import net.b07z.sepia.server.core.assistant.PARAMETERS;
import net.b07z.sepia.server.core.tools.Converters;
import net.b07z.sepia.server.core.tools.Debugger;

/**
 * German/English dictionary API by Linguee and partially others.
 * 
 * @author Florian Quirin
 *
 */
public class Dictionary_Linguee implements ApiInterface{
	
	//--- data ---
	public static String getButtonText(String language){
		if (language.equals(LANGUAGES.DE)){
			return "Wörterbuch öffnen";
		}else{
			return "Open dictionary";
		}
	}
	//-------------
	
	//info
	public ApiInfo getInfo(String language){
		return new ApiInfo(Type.link, Content.redirect, true);
	}

	//result
	public ApiResult getResult(NluResult NLU_result){
		//initialize result
		API api = new API(NLU_result);
		
		//get parameters
		String search = NLU_result.getParameter(PARAMETERS.SEARCH);
		String target_lang = NLU_result.getParameter(PARAMETERS.LANGUAGE);
		Debugger.println("cmd: dict_translate, search " + search + ", target_lang=" + target_lang, 2);		//debug
		
		//check'em
		if (search.isEmpty()){
			return AskClient.question("dict_translate_ask_0a", "search", NLU_result);
		}
		if (target_lang.isEmpty()){
			//set default target language
			if (NLU_result.language.matches("en")){
				target_lang = "de";
			}else{
				target_lang = "en";
			}
		}
		
		String supported_languages = "(de|en|tr)";		//add languages here when adding more target languages
		
		//make answer - if more than one direct answer choose randomly
		if (target_lang.matches(supported_languages)){
			//supported language
			api.answer = Config.answers.getAnswer(NLU_result, "dict_translate_1a", search, target_lang);
		}else{
			//unsupported target language
			api.answer = Config.answers.getAnswer(NLU_result, "dict_translate_1b", search, target_lang);
		}
		api.answer_clean = Converters.removeHTML(api.answer);
		
		//make action: browser url call
		String call_url = "";
		if (target_lang.matches("(de|en)")){
			try {
				call_url = "http://m.linguee.de/deutsch-englisch/search?source=auto&query=" + URLEncoder.encode(search, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				call_url = "http://m.linguee.de/deutsch-englisch/";
			}
		}else if (target_lang.matches("(tr)")){
			try {
				//call_url = "http://detr.dict.cc/?s=" + URLEncoder.encode(search, "UTF-8");
				call_url = "http://www.seslisozluk.net/de/was-bedeutet-" + URLEncoder.encode(search, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				call_url = "http://detr.dict.cc";
			}
		}else{
			call_url = "http://m.linguee.de/";
		}
		
		//action - for supported languages
		if (target_lang.matches(supported_languages)){
			api.actionInfo_add_action(ACTIONS.OPEN_IN_APP_BROWSER);
			api.actionInfo_put_info("url", call_url);

			api.actionInfo_add_action(ACTIONS.BUTTON_IN_APP_BROWSER);
			api.actionInfo_put_info("url", call_url); 
			api.actionInfo_put_info("title", getButtonText(api.language));
			
			api.hasAction = true;
		}
		
		//build card
		/*
		Card card = new Card();
		String card_text = "<b>Dictionary</b><br><br>" + "<b>Search: </b>"+ search; //+"<br>" + "<b>Language: </b>" + target_lang;
		String card_img = Config.url_web_images + "linguee-logo.png";
		card.addElement(card_text, call_url, card_img);
		//add it
		api.cardInfo = card.cardInfo;
		api.hasCard = false;
		*/
		
		//build html
		if (CLIENTS.hasWebView(NLU_result.input.client_info)){
			api.htmlInfo = "<object type='text/html' style='width: 100%; height: 400%; overflow-y: hidden;' data='" + call_url + "'></object>";
		}else{
			api.htmlInfo = call_url;
		}
		api.hasInfo = true;	
		
		api.status = "success";
				
		//finally build the API_Result
		ApiResult result = api.build_API_result();
				
		//return result_JSON.toJSONString();
		return result;
	}

}

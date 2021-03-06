package net.b07z.sepia.server.assist.interviews;

import net.b07z.sepia.server.assist.interpreters.NluResult;
import net.b07z.sepia.server.assist.server.Config;
import net.b07z.sepia.server.assist.services.ServiceBuilder;
import net.b07z.sepia.server.assist.services.ServiceResult;
import net.b07z.sepia.server.core.tools.Converters;

/**
 * Abort actions.
 * 
 * @author Florian Quirin
 *
 */
public class Abort {
	
	public static ServiceResult get(NluResult NLU_result){
		//initialize result
		ServiceBuilder api = new ServiceBuilder(NLU_result);
		
		//make answer - if more than one direct answer choose randomly
		api.answer = Config.answers.getAnswer(NLU_result, "abort_0a");
		api.answerClean = Converters.removeHTML(api.answer);
		
		api.status = "success";
				
		//finally build the API_Result
		ServiceResult result = api.buildResult();
				
		//return result_JSON.toJSONString();
		return result;
		
	}

}

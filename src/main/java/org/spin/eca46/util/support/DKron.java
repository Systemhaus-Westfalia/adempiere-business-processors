/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, C.A.                      *
 * Contributor(s): Yamel Senih ysenih@erpya.com                                      *
 * This program is free software: you can redistribute it and/or modify              *
 * it under the terms of the GNU General Public License as published by              *
 * the Free Software Foundation, either version 3 of the License, or                 *
 * (at your option) any later version.                                               *
 * This program is distributed in the hope that it will be useful,                   *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                    *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                     *
 * GNU General Public License for more details.                                      *
 * You should have received a copy of the GNU General Public License                 *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.spin.eca46.util.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MScheduler;
import org.compiere.util.Env;
import org.compiere.util.Util;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.json.JSONObject;
import org.spin.model.MADAppRegistration;

/**
 * 	Util class for some helper methods
 * 	@author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class DKron implements IExternalProcessor {
	/**	dKron Host	*/
	private String dKronHost = null;
	/**	ADempier Token	*/
	private String adempiereToken = null;
	/**	ADempiere Host	*/
	private String adempiereHost = null;
	/**	Registration Id	*/
	private int registrationId = 0;
	private final String ADEMPIERE_TOKEN = "adempiere_token";
	private final String ADEMPIERE_HOST = "adempiere_host";
	
	
	/**
	 * Validate connection
	 */
	private void validate() {
		if(getAppRegistrationId() <= 0) {
			throw new AdempiereException("@AD_AppRegistration_ID@ @NotFound@");
		}
		MADAppRegistration registration = MADAppRegistration.getById(Env.getCtx(), getAppRegistrationId(), null);
		adempiereHost = registration.getParameterValue(ADEMPIERE_HOST);
		adempiereToken = registration.getParameterValue(ADEMPIERE_TOKEN);
		dKronHost = registration.getHost();
		//	dKron Host
		if(Util.isEmpty(dKronHost)) {
			throw new AdempiereException("@Host@ @NotFound@");
		}
		//	ADempiere Host
		if(Util.isEmpty(adempiereHost)) {
			throw new AdempiereException("ADempiere @Host@ @NotFound@");
		}
		//	ADempiere Token
		if(Util.isEmpty(adempiereToken)) {
			throw new AdempiereException("@Token@ @NotFound@");
		}
		if(registration.getPort() > 0) {
			dKronHost = dKronHost + ":" + registration.getPort();
		}
	}

	@Override
	public String testConnection() {
		return "Ok";
	}

	@Override
	public void setAppRegistrationId(int registrationId) {
		this.registrationId = registrationId;
		validate();
	}

	@Override
	public int getAppRegistrationId() {
		return registrationId;
	}

	@Override
	public String exportProcessor(IProcessorEntity processor) {
		Invocation.Builder invocationBuilder = getClient().target(dKronHost)
				.path("v1")
				.path("jobs")
    			.request(MediaType.APPLICATION_JSON)
    			.header(HttpHeaders.ACCEPT, "application/json");
		//	
		JSONObject jsonValue = new JSONObject(getRequestDefinition(processor));
		Entity<String> entity = Entity.json(jsonValue.toString());
        Response response = invocationBuilder.post(entity);
        if(response.getStatus() != 201
        		|| response.getStatus() != 200) {
        	String output = response.readEntity(String.class);
        	return output;
        }
		return null;
	}
	
	/**
	 * Get Definition for dKron request
	 * @param processor
	 * @return
	 */
	private Map<String, Object> getRequestDefinition(IProcessorEntity processor) {
		Map<String, Object> data = new HashMap<>();
		data.put("name", processor.getIdentifier());
		data.put("displayname", processor.getDisplayName());
		data.put("schedule", getSchedule(processor));
		data.put("timezone", processor.getTimeZone());
		data.put("disabled", !processor.isEnabled());
		data.put("retries", 0);
		data.put("concurrency", "forbid");
		data.put("executor", "http");
		Map<String, Object> executorConfig = new HashMap<>();
		executorConfig.put("method", "POST");
		executorConfig.put("url", adempiereHost);
		List<String> headers = new ArrayList<>();
		headers.add("\"Authorization: Bearer " + adempiereToken + "\"");
		headers.add("\"Content-Type: application/json\"");
		executorConfig.put("headers", headers.toString());
		Map<String, Object> process = new HashMap<>();
		process.put("\"process_code\"", "\"" + processor.getProcessCode() + "\"");
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("\"key\"", "\"" + processor.getProcessorParameterCode() + "\"");
		parameters.put("\"integer_value\"", processor.getProcessorParameterId());
		List<String> parameterList = new ArrayList<>();
		parameterList.add(parameters.toString());
		process.put("\"parameters\"", parameterList.toString());
		Map<String, Object> body = new HashMap<>();
		body.put("\"process\"", process.toString());
		executorConfig.put("body", body.toString().replaceAll("=", ": "));
		executorConfig.put("timeout", "60000");
		executorConfig.put("expectCode", "200");
		executorConfig.put("expectBody", "");
		executorConfig.put("debug", "false");
		data.put("executor_config", executorConfig);
		return data;
	}
	
	private String getSchedule(IProcessorEntity processor) {
		String schedule = null;
		if(processor.getFrequency() <= 0) {
			switch (processor.getFrequencyType()) {
			case MScheduler.FREQUENCYTYPE_Hour:
				schedule = "@hourly";
				break;
			case MScheduler.FREQUENCYTYPE_Minute:
				schedule = "@minutely";
				break;
			case MScheduler.FREQUENCYTYPE_Secound:
				schedule = "@every 1s";		
				break;
			case MScheduler.FREQUENCYTYPE_Yearly:
				schedule = "@yearly";
				break;
			case MScheduler.FREQUENCYTYPE_Monthly:
				schedule = "@monthly";
				break;
			case MScheduler.FREQUENCYTYPE_Weekly:
				schedule = "@weekly";
				break;
			case MScheduler.FREQUENCYTYPE_Day:
				schedule = "@daily";
				break;
			case MScheduler.FREQUENCYTYPE_Biweekly:
				schedule = "@every 48h";
				break;
			case MScheduler.FREQUENCYTYPE_Quarterly:
				schedule = "@every 48h";
				break;
			default:
				//	Default hours
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			}	
		} else {
			switch (processor.getFrequencyType()) {
			case MScheduler.FREQUENCYTYPE_Hour:
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Minute:
				schedule = "@every " + processor.getFrequency() + "m";
				break;
			case MScheduler.FREQUENCYTYPE_Secound:
				schedule = "@every " + processor.getFrequency() + "s";		
				break;
			case MScheduler.FREQUENCYTYPE_Yearly:
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Monthly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 30) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Weekly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 7) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Day:
				schedule = "@every " + (processor.getFrequency()  * 24) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Biweekly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 60) + "h";
				break;
			case MScheduler.FREQUENCYTYPE_Quarterly:
				schedule = "@every " + (processor.getFrequency()  * 24 * 60) + "h";
				break;
			default:
				//	Default hours
				schedule = "@every " + processor.getFrequency() + "h";
				break;
			}
		}
		return schedule;
	}

	/**
	 * Get client
	 * @return
	 */
	public Client getClient() {
		return ClientBuilder.newClient(new ClientConfig())
		.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
	}
}

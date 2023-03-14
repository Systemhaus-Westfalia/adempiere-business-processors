/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2015 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpya.com                                  *
 *****************************************************************************/
package org.spin.eca46.setup;

import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.Query;
import org.spin.eca46.util.support.DKron;
import org.spin.model.MADAppRegistration;
import org.spin.model.MADAppSupport;
import org.spin.util.ISetupDefinition;

/**
 * Create a Setup for dKron and all registration, please add adempiere_token parameter
 * @author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class CreateDKron implements ISetupDefinition {

	private static final String DESCRIPTION = "(*Created from Setup Automatically*)";
	private static final String NAME = "Default Processor Exporter";
	private static final String APPLICATION_TYPE = "EPR";
	
	@Override
	public String doIt(Properties context, String transactionName) {
		//	For setup
		createRegistration(context, transactionName);
		//	financial management
		return "@AD_SetupDefinition_ID@ @Ok@";
	}
	
	/**
	 * Create Model Vaidator
	 * @param context
	 * @param transactionName
	 * @return
	 */
	private void createRegistration(Properties context, String transactionName) {
		MADAppRegistration registration = new Query(context, MADAppRegistration.Table_Name, "EXISTS(SELECT 1 FROM AD_AppSupport s "
				+ "WHERE s.AD_AppSupport_ID = AD_AppRegistration.AD_AppSupport_ID "
				+ "AND s.ApplicationType = ?"
				+ "AND s.IsActive = 'Y'"
				+ "AND s.Classname = ?)", transactionName)
				.setParameters(APPLICATION_TYPE, DKron.class.getName())
				.setClient_ID()
				.<MADAppRegistration>first();
		//	Validate
		if(registration != null
				&& registration.getAD_AppRegistration_ID() > 0) {
			return;
		}
		//	Get
		MADAppSupport applicationSupport = new Query(context, MADAppSupport.Table_Name, "Classname = ? AND ApplicationType = ?", transactionName).setParameters(DKron.class.getName(), APPLICATION_TYPE).first();
		//	
		if(applicationSupport == null) {
			throw new AdempiereException("@AD_AppSupport_ID@ @NotFound@");
		}
		registration = new MADAppRegistration(context, 0, transactionName);
		registration.setApplicationType(applicationSupport.getApplicationType());
		registration.setAD_AppSupport_ID(applicationSupport.getAD_AppSupport_ID());
		registration.setAD_Org_ID(0);
		registration.setValue("EPR-dKron");
		registration.setName(NAME);
		registration.setDescription(DESCRIPTION);
		registration.setVersionNo("1.0");
		//	dKron host and endpoint
		registration.setHost("http://localhost:8080/v1/jobs");
		registration.setPort(8080);
		registration.setTimeout(0);
		registration.saveEx();
	}
}

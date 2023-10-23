/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * This program is free software; you can redistribute it and/or modify it    		 *
 * under the terms version 2 or later of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope   		 *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied 		 *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           		 *
 * See the GNU General Public License for more details.                       		 *
 * You should have received a copy of the GNU General Public License along    		 *
 * with this program; if not, write to the Free Software Foundation, Inc.,    		 *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     		 *
 * For the text or an alternative of this public license, you may reach us    		 *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com				  		                 *
 *************************************************************************************/
package org.spin.eca46.util.support;

/** 
 * 	Interface as contract for supported API
 *  @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
public interface IProcessorEntity {
	
	public static final int ACCOUNTING = 1;
	public static final int PROJECT = 2;
	public static final int REQUEST = 3;
	public static final int ALERT = 4;
	public static final int SCHEDULER = 5;
	public static final int WORKFLOW = 6;
	
	/**
	 * Get
	 * @return
	 */
	public int getProcessorType();
	
	/**
	 * Get Identifier of Entity
	 * @return
	 */
	public String getIdentifier();
	
	/**
	 * Get Display Name for Processor
	 * @return
	 */
	public String getDisplayName();
	
	/**
	 * Get Execution Time Zone
	 * @return
	 */
	public String getTimeZone();
	
	/**
	 * Get Process Code for running
	 * @return
	 */
	public String getProcessCode();
	
	/**
	 * Get Processor Parameter Code, some like:
	 * AD_Scheduler_ID for run ECA46_Run_Scheduler_Processor
	 * @return
	 */
	public String getProcessorParameterCode();
	
	/**
	 * Get Processor paramerer ID, some like:
	 * 100 for run ECA46_Run_Scheduler_Processor
	 * @return
	 */
	public int getProcessorParameterId();
	
	
	/**
	 * Get Frequency Type
	 * @return
	 */
	public String getFrequencyType();
	
	/**
	 * Get Execution frequency
	 * @return
	 */
	public int getFrequency();
	
	/**
	 * Validate if is enabled it
	 * @return
	 */
	public boolean isEnabled();
}

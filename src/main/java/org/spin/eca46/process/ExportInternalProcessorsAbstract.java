/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2006-2017 ADempiere Foundation, All Rights Reserved.         *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * or (at your option) any later version.										*
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY, without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program, if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * or via info@adempiere.net or http://www.adempiere.net/license.html         *
 *****************************************************************************/

package org.spin.eca46.process;

import org.compiere.process.SvrProcess;

/** Generated Process for (Export Internal Processors)
 *  @author ADempiere (generated) 
 *  @version Release 3.9.3
 */
public abstract class ExportInternalProcessorsAbstract extends SvrProcess {
	/** Process Value 	*/
	private static final String VALUE_FOR_PROCESS = "ECA46_Export_Internal_Processors";
	/** Process Name 	*/
	private static final String NAME_FOR_PROCESS = "Export Internal Processors";
	/** Process Id 	*/
	private static final int ID_FOR_PROCESS = 54663;
	/**	Parameter Name for External Processor	*/
	public static final String EXTERNALPROCESSOR_ID = "ExternalProcessor_ID";
	/**	Parameter Value for External Processor	*/
	private int externalProcessorId;

	@Override
	protected void prepare() {
		externalProcessorId = getParameterAsInt(EXTERNALPROCESSOR_ID);
	}

	/**	 Getter Parameter Value for External Processor	*/
	protected int getExternalProcessorId() {
		return externalProcessorId;
	}

	/**	 Setter Parameter Value for External Processor	*/
	protected void setExternalProcessorId(int externalProcessorId) {
		this.externalProcessorId = externalProcessorId;
	}

	/**	 Getter Parameter Value for Process ID	*/
	public static final int getProcessId() {
		return ID_FOR_PROCESS;
	}

	/**	 Getter Parameter Value for Process Value	*/
	public static final String getProcessValue() {
		return VALUE_FOR_PROCESS;
	}

	/**	 Getter Parameter Value for Process Name	*/
	public static final String getProcessName() {
		return NAME_FOR_PROCESS;
	}
}
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

import java.util.TimeZone;

import org.eevolution.model.MProjectProcessor;
import org.spin.eca46.process.ProjectProcessor;
/**
 * 	Wrapper for Project Processor
 * 	@author Yamel Senih, ysenih@erpya.com, ERPCyA http://www.erpya.com
 */
public class Project implements IProcessorEntity {
	/**	Project processor	*/
	private MProjectProcessor processor;
	
	/**
	 * Static builder
	 * @return
	 */
	public static Project newInstance() {
		return new Project();
	}
	
	/**
	 * Set Project Processor
	 * @param processor
	 * @return
	 */
	public Project withProjectProcessor(MProjectProcessor processor) {
		this.processor = processor;
		return this;
	}

	@Override
	public String getIdentifier() {
		return (ProjectProcessor.getProcessValue() + "_" + processor.getC_ProjectProcessor_ID()).toLowerCase();
	}

	@Override
	public String getDisplayName() {
		return processor.getName();
	}

	//	America/Caracas or any
	@Override
	public String getTimeZone() {
		return TimeZone.getDefault().getID();
	}

	@Override
	public String getProcessCode() {
		return ProjectProcessor.getProcessValue();
	}

	@Override
	public String getProcessorParameterCode() {
		return ProjectProcessor.C_PROJECTPROCESSOR_ID;
	}

	@Override
	public int getProcessorParameterId() {
		return processor.getC_ProjectProcessor_ID();
	}



	@Override
	public String getFrequencyType() {
		return processor.getFrequencyType();
	}

	@Override
	public int getFrequency() {
		return processor.getFrequency();
	}

	@Override
	public boolean isEnabled() {
		return processor.isActive();
	}
}

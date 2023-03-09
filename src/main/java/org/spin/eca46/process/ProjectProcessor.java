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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MColumn;
import org.compiere.model.MMailText;
import org.compiere.model.MNote;
import org.compiere.model.MProject;
import org.compiere.model.MProjectPhase;
import org.compiere.model.MProjectProcessorChange;
import org.compiere.model.MProjectProcessorQueued;
import org.compiere.model.MProjectTask;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.project.services.ProjectProcessorService;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.eevolution.model.MProjectProcessor;
import org.eevolution.model.MProjectProcessorLog;
import org.eevolution.model.MProjectStatus;
import org.spin.queue.notification.DefaultNotifier;
import org.spin.queue.util.QueueLoader;

/** 
 * 	Generated Process for (Product Internal Use)
 *  @author Jorg Janke
 *  @version $Id: RequestProcessor.java,v 1.3 2006/07/30 00:53:33 jjanke Exp $
 *  @version Release 3.9.3
 */
public class ProjectProcessor extends ProjectProcessorAbstract {
	
	/**	Initial work time			*/
	private long 				startWork;
	/**	The Concrete Model			*/
	private MProjectProcessor	projectProcessor = null;
	
	/**	Last Summary				*/
	private StringBuffer 		summary = new StringBuffer();
	
	/**Current PO Changes*/
	private PO					entity = null;
	
	/**Columns Changeg*/
	private ArrayList<String> 	columns = new ArrayList<String>();
	
	/**Values Changed*/
	private ArrayList<String> 	values = new ArrayList<String>();
	
	/**Current Poject*/
	private MProject 			project = null;
	
	/**Current Project Phase*/
	private MProjectPhase		projectPhase = null;
	
	/**Current Project Task*/
	private MProjectTask		projectTask = null;
	
	/**Prefix Subject for Mail to Send*/
	private String				prefixSubject = "";
	
	/**Prefix Text for Mail to Send*/
	private String				prefixTextMail = "";
	
	/**No HTML Text for Notice */
	private String 				textNotice	= "";
	
	/**Mail Template for Project Processor */
	private MMailText			mailText = null;
	
	/**Extra Message for Mail*/
	private String extraMsg	= "";

	/**Changes is Scheduled*/
	private boolean isScheduled = false;
	
	@Override
	protected void prepare() {
		super.prepare();
		if(getProjectProcessorId() <= 0) {
			throw new AdempiereException("@C_ProjectProcessor_ID@ @NotFound@");
		}
		projectProcessor = new MProjectProcessor(getCtx(), getProjectProcessorId(), get_TrxName());
	}

	@Override
	protected String doIt() throws Exception {
		startWork = System.currentTimeMillis();
		summary = new StringBuffer();
		mailText = new MMailText (Env.getCtx(), projectProcessor.getR_MailText_ID(), null);
		//
		clearGlobals();
		//Process Queued Manual Changes
		processQueued();
		
		//Process Scheduled Changes
		processProjects ();
		processQueued();
		return TimeUtil.formatElapsed(new Timestamp(startWork));
	}
	
	/**************************************************************************
	 *  Process Projects.
	 *  Scheduled - are they due?
	 */
	private void processProjects ()
	{
		//Start Date Alert
		processStartDateAlert();
		
		//Process Status 
		processStatus();
				
		//Due Alerts
		processDueAlert();
		
		//Overdue Alerts
		processOverDueAlert();
	}	//  processProject

	/**
	 * Process Queued for e-mail messages
	 */
	private void processQueued() {
		
		String whereClause = " EXISTS (SELECT 1 "
								+ "FROM C_ProjectProcessorQueued q "
								+ "WHERE q.SendEmail='N' AND q.C_ProjectProcessorLog_ID = C_ProjectProcessorLog.C_ProjectProcessorLog_ID )";
		MProjectProcessorLog[] pLogs = projectProcessor.getProcessorLogs(whereClause);
		boolean loadMsj = false;
		for (MProjectProcessorLog mProjectProcessorLog : pLogs) {
			loadMsj = loadMsgToSend(mProjectProcessorLog);
			MProjectProcessorQueued[] queued = mProjectProcessorLog.getQueued("SendEmail='N'");
			for (MProjectProcessorQueued mProjectProcessorQueued : queued) {
				if (loadMsj) {
					String NotificationType = (Util.isEmpty(mProjectProcessorQueued.getNotificationType()) ? MProjectProcessorQueued.NOTIFICATIONTYPE_None : mProjectProcessorQueued.getNotificationType()) ; 
					if (NotificationType.equals(MProjectProcessorQueued.NOTIFICATIONTYPE_EMail))
						sendEmail(mProjectProcessorQueued);
					else if (NotificationType.equals(MProjectProcessorQueued.NOTIFICATIONTYPE_EMailPlusNotice)) {
						sendEmail(mProjectProcessorQueued);
						createNotice(mProjectProcessorQueued);
					}
					else if (NotificationType.equals(MProjectProcessorQueued.NOTIFICATIONTYPE_Notice)) 
						createNotice(mProjectProcessorQueued);
				}
				mProjectProcessorQueued.setSendEMail(true);
				mProjectProcessorQueued.save();
			}
			//Clear Globals
			clearGlobals();
		}			
	}	//processQueued

	/**
	 * Send Email
	 * @param log
	 * @param queued
	 * @return
	 */
	private boolean sendEmail (MProjectProcessorQueued queued) {
		MUser recipient = (MUser) queued.getAD_User();
		mailText.setUser(recipient);
		if (entity != null) {
			mailText.setPO(entity);
		}
		//	
		Trx.run(transactionName -> {
			String subject = (!Util.isEmpty(mailText.getMailHeader())  ? mailText.getMailHeader() + " " :mailText.getMailHeader()) + prefixSubject;
			String message = "";
			if (mailText.getR_MailText_ID() != 0) {
				message = mailText.getMailText(true);
			}
			//	Get instance for notifier
			DefaultNotifier notifier = (DefaultNotifier) QueueLoader.getInstance().getQueueManager(DefaultNotifier.QUEUETYPE_DefaultNotifier)
					.withContext(getCtx())
					.withTransactionName(transactionName);
			//	Send notification to queue
			notifier
				.clearMessage()
				.withApplicationType(DefaultNotifier.DefaultNotificationType_UserDefined)
				.withText(prefixTextMail + (message == null ? "" : message)  + "\n")
				.withDescription(subject);
			//	Add user
			if (projectProcessor.getSupervisor_ID() != 0) {
				notifier.withUserId(projectProcessor.getSupervisor_ID());
			}
			//	Add recipients
			notifier.addRecipient(queued.getAD_User_ID());
			if(entity != null) {
				notifier
					.withEntity(entity);
			}
			//	Add to queue
			notifier.addToQueue();
		});
		return true;
	}   //  sendEmail
	
	/**
	 * Send Email
	 * @param log
	 * @param queued
	 * @return
	 */
	private boolean createNotice (MProjectProcessorQueued queued){
		MUser to = (MUser) queued.getAD_User();
		if (to.getAD_User_ID()!=0) {
			MNote note = new MNote(getCtx(), "Error", to.getAD_User_ID(), entity.get_TrxName());
			note.setRecord(entity.get_Table_ID(), entity.get_ID());
			note.setReference(prefixSubject);
			note.setTextMsg(textNotice);
			if (!note.save())
				return false;
		}
		
		return true;
	}   //  createNotice


	/**
	 * Process Project Status
	 */
	private void processStatus()
	{
		//Process Project Status
		String whereClause = "";
		Timestamp currentDate = new Timestamp(TimeUtil.getToday().getTimeInMillis());
		
		whereClause = "EXISTS (SELECT 1 FROM "
				+ "C_ProjectStatus ps "
				+ "WHERE ps.C_ProjectStatus_ID = C_Project.C_ProjectStatus_ID "
				+ "AND ps.TimeoutDays > 0 AND ps.Next_Status_ID > 0 "
				+ "AND C_Project.DateLastAction + ps.TimeoutDays < ?) ";
		
		new ScheduleChange(getCtx(), MProject.Table_Name, whereClause, projectProcessor.get_TrxName())
				.setAlertMessageColumn("@C_ProjectStatus_ID@ @HasBeenChanged@")
				.IsNextProjectStatus()
				.setParameters(currentDate)
				.processScheduleChanges();
		
		
		//Process Project Phase Status
		whereClause = "EXISTS (SELECT 1 FROM "
				+ "C_ProjectStatus ps "
				+ "WHERE ps.C_ProjectStatus_ID = C_ProjectPhase.C_ProjectStatus_ID "
				+ "AND ps.TimeoutDays > 0 AND ps.Next_Status_ID > 0 "
				+ "AND C_ProjectPhase.DateLastAction + ps.TimeoutDays < ?) ";
		
		new ScheduleChange(getCtx(), MProjectPhase.Table_Name, whereClause, projectProcessor.get_TrxName())
				.setAlertMessageColumn("@C_ProjectStatus_ID@ @HasBeenChanged@")
				.IsNextProjectStatus()
				.setParameters(currentDate)
				.processScheduleChanges();

		//Process Project Task Status
		whereClause = "EXISTS (SELECT 1 FROM "
				+ "C_ProjectStatus ps "
				+ "WHERE ps.C_ProjectStatus_ID = C_ProjectTask.C_ProjectStatus_ID "
				+ "AND ps.TimeoutDays > 0 AND ps.Next_Status_ID > 0 "
				+ "AND C_ProjectTask.DateLastAction + ps.TimeoutDays < ?) ";

		new ScheduleChange(getCtx(), MProjectTask.Table_Name, whereClause, projectProcessor.get_TrxName())
				.setAlertMessageColumn("@C_ProjectStatus_ID@ @HasBeenChanged@")
				.IsNextProjectStatus()
				.setParameters(currentDate)
				.processScheduleChanges();
	}	//	processStatus
	
	/**
	 * Process Project Start Date
	 */
	private void processStartDateAlert() {
		
		String whereClauseGeneral = "";
		String whereClause = "";
		isScheduled = true;
		ArrayList<Object> params = new ArrayList<Object>();
		Timestamp currentDate = new Timestamp(TimeUtil.getToday().getTimeInMillis());
		int daysBeforeStart = projectProcessor.get_ValueAsInt("DaysBeforeStart");
		//Project
		whereClauseGeneral =  "(DateStartSchedule IS NOT NULL AND DateStartSchedule - ? <= ? AND DateStartSchedule >= ?) AND (DueType IS NULL OR DueType = ?)";
		params.add(daysBeforeStart);
		params.add(currentDate);
		params.add(currentDate);
		params.add(MProject.DUETYPE_Scheduled);
		
		
		//Remind Days
		whereClauseGeneral+= " AND (DateLastAlert IS NULL OR DateLastAlert!=?)";
		params.add(currentDate);
		
		if (projectProcessor.getC_ProjectType_ID()!=0) {
			whereClause+= " AND C_ProjectType_ID = ?";
			params.add(projectProcessor.getC_ProjectType_ID());
		}
		
		new ScheduleChange(getCtx(), MProject.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
				.setColumns(MProject.COLUMNNAME_DueType,MProject.COLUMNNAME_DateLastAlert)
				.setValues(MProject.DUETYPE_Scheduled,currentDate)
				.setAlertMessageColumn("@C_Project_ID@ @IsFor@ @Start@")
				.setParameters(params)
				.processScheduleChanges();
		
		//Project Phase
		whereClause = "";
		if (projectProcessor.getC_ProjectType_ID()!=0) 
			whereClause+= " AND EXISTS (SELECT 1 FROM C_Project p WHERE p.C_Project_ID = C_ProjectPhase.C_Project_ID AND p.C_ProjectType_ID = ?)";
		
		new ScheduleChange(getCtx(), MProjectPhase.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
				.setColumns(MProjectPhase.COLUMNNAME_DueType,MProjectPhase.COLUMNNAME_DateLastAlert)
				.setValues(MProject.DUETYPE_Scheduled,currentDate)
				.setAlertMessageColumn("@C_ProjectPhase_ID@ @IsFor@ @Start@")
				.setParameters(params)
				.processScheduleChanges();
		
		//Project Task
		whereClause = "";
		if (projectProcessor.getC_ProjectType_ID()!=0) 
			whereClause+= " AND EXISTS (SELECT 1 "
										+ "FROM C_Project p "
										+ "INNER JOIN C_ProjectPhase ph ON (p.C_Project_ID = ph.C_Project_ID) "
										+ "WHERE ph.C_ProjectPhase_ID = C_ProjectTask.C_ProjectPhase_ID AND p.C_ProjectType_ID = ?)";
		
		if (projectProcessor.getC_ProjectTaskCategory_ID()!=0) {
			whereClause+= " AND C_ProjectTaskCategory_ID=?";
			params.add(projectProcessor.getC_ProjectTaskCategory_ID());
		}
			
		new ScheduleChange(getCtx(), MProjectTask.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
				.setColumns(MProjectTask.COLUMNNAME_DueType,MProjectTask.COLUMNNAME_DateLastAlert)
				.setValues(MProject.DUETYPE_Scheduled,currentDate)
				.setAlertMessageColumn("@C_ProjectTask_ID@ @IsFor@ @Start@")
				.setParameters(params)
				.processScheduleChanges();
	}	//processStartDateAlert
	
	/**
	 * Process Project Due
	 */
	private void processDueAlert() {
		if (projectProcessor.getOverdueAlertDays()!=0) {
			String whereClauseGeneral = "";
			String whereClause = ""; 
			isScheduled =true;
			ArrayList<Object> params = new ArrayList<Object>();
			Timestamp currentDate = new Timestamp(TimeUtil.getToday().getTimeInMillis());
			//General Filters
			whereClauseGeneral =  "(COALESCE(DateDeadLine,DateFinishSchedule) IS NOT NULL) AND (DueType IS NULL OR DueType = ?) ";
			
			params.add(MProject.DUETYPE_Scheduled);
			
			//Due Days Alert
			whereClauseGeneral += " AND (COALESCE(DateDeadLine,DateFinishSchedule) + ? <= ? AND COALESCE(DateDeadLine,DateFinishSchedule) >= ?) ";
			params.add(projectProcessor.getOverdueAlertDays());
			params.add(currentDate);
			params.add(currentDate);
			
			//Project
			whereClause = "AND  (DateFinish IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) {
				whereClause+= " AND (C_ProjectType_ID = ?)";
				params.add(projectProcessor.getC_ProjectType_ID());
			}
			
			
			
			new ScheduleChange(getCtx(), MProject.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProject.COLUMNNAME_DueType,MProject.COLUMNNAME_DateLastAlert)
					.setValues(MProject.DUETYPE_Due,currentDate)
					.setAlertMessageColumn("@C_Project_ID@ @IsDue@")
					.setParameters(params)
					.processScheduleChanges();
			
			//Project Phase
			whereClause = "AND  (EndDate IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) 
				whereClause+= " AND EXISTS (SELECT 1 FROM C_Project p WHERE p.C_Project_ID = C_ProjectPhase.C_Project_ID AND p.C_ProjectType_ID = ?) ";
			
			new ScheduleChange(getCtx(), MProjectPhase.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProjectPhase.COLUMNNAME_DueType,MProjectPhase.COLUMNNAME_DateLastAlert)
					.setValues(MProjectPhase.DUETYPE_Due,currentDate)
					.setAlertMessageColumn("@C_ProjectPhase_ID@ @IsDue@")
					.setParameters(params)
					.processScheduleChanges();
			
			//Project Task
			whereClause = "AND  (DateFinish IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) 
				whereClause+= " AND (EXISTS (SELECT 1 "
											+ "FROM C_Project p "
											+ "INNER JOIN C_ProjectPhase ph ON (p.C_Project_ID = ph.C_Project_ID) "
											+ "WHERE ph.C_ProjectPhase_ID = C_ProjectTask.C_ProjectPhase_ID AND p.C_ProjectType_ID = ?))";
			
			if (projectProcessor.getC_ProjectTaskCategory_ID()!=0) {
				whereClause+= " AND (C_ProjectTaskCategory_ID=?)";
				params.add(projectProcessor.getC_ProjectTaskCategory_ID());
			}
				
			new ScheduleChange(getCtx(), MProjectTask.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProjectTask.COLUMNNAME_DueType,MProjectTask.COLUMNNAME_DateLastAlert)
					.setValues(MProjectTask.DUETYPE_Due,currentDate)
					.setAlertMessageColumn("@C_ProjectTask_ID@ @IsDue@")
					.setParameters(params)
					.processScheduleChanges();
		}
	}	//processDueAlert
	
	/**
	 * Process Project Due
	 */
	private void processOverDueAlert() {
		if (projectProcessor.getOverdueAssignDays()!=0) {
			String whereClauseGeneral = "";
			String whereClause = ""; 
			isScheduled = true;
			ArrayList<Object> params = new ArrayList<Object>();
			Timestamp currentDate = new Timestamp(TimeUtil.getToday().getTimeInMillis());
			//General Filters
			whereClauseGeneral =  "(COALESCE(DateDeadLine,DateFinishSchedule) IS NOT NULL) AND (DueType IS NULL OR DueType IN (?,?,?)) ";
			
			params.add(MProject.DUETYPE_Scheduled);
			params.add(MProject.DUETYPE_Due);
			params.add(MProject.DUETYPE_Overdue);
			
			//Due Days Alert
			whereClauseGeneral += " AND (COALESCE(DateDeadLine,DateFinishSchedule) + ? >= ? AND COALESCE(DateDeadLine,DateFinishSchedule) < ?) ";
			params.add(projectProcessor.getOverdueAssignDays());
			params.add(currentDate);
			params.add(currentDate);
			
			//Project
			whereClause = "AND  (DateFinish IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) {
				whereClause+= " AND (C_ProjectType_ID = ?)";
				params.add(projectProcessor.getC_ProjectType_ID());
			}
			
			
			
			new ScheduleChange(getCtx(), MProject.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProject.COLUMNNAME_DueType,MProject.COLUMNNAME_DateLastAlert)
					.setValues(MProject.DUETYPE_Overdue,currentDate)
					.setAlertMessageColumn("@C_Project_ID@ @OverDue@")
					.setParameters(params)
					.processScheduleChanges();
			
			//Project Phase
			whereClause = "AND  (EndDate IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) 
				whereClause+= " AND EXISTS (SELECT 1 FROM C_Project p WHERE p.C_Project_ID = C_ProjectPhase.C_Project_ID AND p.C_ProjectType_ID = ?) ";
			
			new ScheduleChange(getCtx(), MProjectPhase.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProjectPhase.COLUMNNAME_DueType,MProjectPhase.COLUMNNAME_DateLastAlert)
					.setValues(MProjectPhase.DUETYPE_Overdue,currentDate)
					.setAlertMessageColumn("@C_ProjectPhase_ID@ @OverDue@")
					.setParameters(params)
					.processScheduleChanges();
			
			//Project Task
			whereClause = "AND  (DateFinish IS NULL) ";
			if (projectProcessor.getC_ProjectType_ID()!=0) 
				whereClause+= " AND (EXISTS (SELECT 1 "
											+ "FROM C_Project p "
											+ "INNER JOIN C_ProjectPhase ph ON (p.C_Project_ID = ph.C_Project_ID) "
											+ "WHERE ph.C_ProjectPhase_ID = C_ProjectTask.C_ProjectPhase_ID AND p.C_ProjectType_ID = ?))";
			
			if (projectProcessor.getC_ProjectTaskCategory_ID()!=0) {
				whereClause+= " AND (C_ProjectTaskCategory_ID=?)";
				params.add(projectProcessor.getC_ProjectTaskCategory_ID());
			}
				
			new ScheduleChange(getCtx(), MProjectTask.Table_Name, whereClauseGeneral + whereClause, projectProcessor.get_TrxName())
					.setColumns(MProjectTask.COLUMNNAME_DueType,MProjectTask.COLUMNNAME_DateLastAlert)
					.setValues(MProjectTask.DUETYPE_Overdue,currentDate)
					.setAlertMessageColumn("@C_ProjectTask_ID@ @OverDue@")
					.setParameters(params)
					.processScheduleChanges();
		}
	}	//processOverDueAlert
	
	/**
	 * 	Get Server Info
	 *	@return info
	 */
	public String getServerInfo()
	{
		return "Last=" + summary.toString();
	}	//	getServerInfo
	
	/**
	 * Load Message to Send
	 * @param log
	 */
	private boolean loadMsgToSend(MProjectProcessorLog log) {
		
		MProjectProcessorChange[] changes = log.getChange(null);
		MProjectStatus status = null;
		String nameHeader = "";
		String itemStatus = "";
		MUser createdBy = null;
		MUser updatedBy = null;
		
		if (changes.length==0)
			return false;
		
		for (MProjectProcessorChange change : changes) {
			if (entity == null) {
				if (change.getAD_Table_ID()==MProject.Table_ID) {
					project = new MProject (getCtx(), change.getRecord_ID(), change.get_TrxName());
					entity = project;
				}else if (change.getAD_Table_ID()==MProjectPhase.Table_ID) {
					projectPhase = new MProjectPhase (getCtx(), change.getRecord_ID(), change.get_TrxName());
					project = (MProject) projectPhase.getC_Project();
					entity = projectPhase;
				}else if (change.getAD_Table_ID()==MProjectTask.Table_ID) {
					projectTask = new MProjectTask (getCtx(), change.getRecord_ID(), change.get_TrxName());
					projectPhase = (MProjectPhase) projectTask.getC_ProjectPhase();
					project = (MProject) projectPhase.getC_Project();
					entity = projectPhase;
				}
			}

			if (entity!=null 
					&& isScheduled) 
				extraMsg = entity.get_ValueAsString(MProject.COLUMNNAME_AlertMessage);
			
			if (change.getAD_Column_ID()!=0) {
				MColumn col = MColumn.get (change.getCtx(), change.getAD_Column_ID());
				columns.add(col.getColumnName());
				values.add(change.getNewValue());
			}
		}
		
		if (project != null) 
			prefixSubject = "[" + project.getName();
		
		if (projectTask != null) {
			prefixTextMail = Msg.parseTranslation(log.getCtx(), "@C_ProjectTask_ID@") + " #" + projectTask.getSeqNo();
			prefixSubject += (Util.isEmpty(prefixSubject) ? "": " - ") + prefixTextMail; 
			nameHeader = projectTask.getName();
			createdBy = MUser.get(log.getCtx(), projectTask.getCreatedBy());
			updatedBy = MUser.get(log.getCtx(), projectTask.getUpdatedBy());
			if (projectTask.getC_ProjectStatus_ID()!=0) {
				status = (MProjectStatus) projectTask.getC_ProjectStatus(); 
				itemStatus = "(" + status.getName() + ")";
			}
		}else if (projectPhase != null) {
			prefixTextMail =Msg.parseTranslation(log.getCtx(), "@C_ProjectPhase_ID@") + " #" + projectPhase.getSeqNo();
			prefixSubject += (Util.isEmpty(prefixSubject) ? "": " - ") + prefixTextMail;
			nameHeader = projectPhase.getName();
			createdBy = MUser.get(log.getCtx(), projectPhase.getCreatedBy());
			updatedBy = MUser.get(log.getCtx(), projectPhase.getUpdatedBy());
			if (projectPhase.getC_ProjectStatus_ID()!=0) {
				status = (MProjectStatus) projectPhase.getC_ProjectStatus(); 
				itemStatus = "(" + status.getName() + ")";
			}
		}else if (project != null) {
			prefixTextMail =Msg.parseTranslation(log.getCtx(), "@C_Project_ID@") + " " + project.getName();
			createdBy = MUser.get(log.getCtx(), project.getCreatedBy());
			updatedBy = MUser.get(log.getCtx(), project.getUpdatedBy());
			if (project.getC_ProjectStatus_ID()!=0) {
				status = (MProjectStatus) project.getC_ProjectStatus(); 
				itemStatus = "(" + status.getName() + ")";
			}
		}
		
		if (!Util.isEmpty(prefixSubject))
			prefixSubject += "] "; 
		
		prefixSubject += itemStatus + " " + nameHeader;
		if (log.getEventChangeLog()!=null) {
			
			if (log.getEventChangeLog().equals(MProjectProcessorLog.EVENTCHANGELOG_Insert)
					&& createdBy!=null)
				prefixTextMail+= " " +Msg.parseTranslation(log.getCtx(), "@CreatedBy@") + " " + createdBy.getName();
			else if (log.getEventChangeLog().equals(MProjectProcessorLog.EVENTCHANGELOG_Update)
					&& updatedBy!=null) 
				prefixTextMail+= " " +Msg.parseTranslation(log.getCtx(), "@UpdatedBy@") + " " + updatedBy.getName();
			
			textNotice = prefixTextMail; 
			StringBuffer sb = new StringBuffer("<HR>\n")
				.append(prefixTextMail + "\n")
				.append(getMessageColumnsChanged())
				.append(getMessageColumnsStatic());
			
			prefixTextMail = sb.toString();
		}
		
		return true;
	}	//loadMsgToSend
	
	/**
	 * Get columns changed on a message
	 * @return
	 */
	private String getMessageColumnsChanged() {
		StringBuffer sb = new StringBuffer();
		
		if (columns.size()>0
				|| !Util.isEmpty(extraMsg))
			sb.append("<ul>\n");
		
		for (int i=0; i < columns.size(); i++) {
			String column = columns.get(i);
			
			if (ProjectProcessorService.isExcludeColumn(column))
				continue;
			
			String value = values.get(i);
			String item = getItemPO(column,value);
			textNotice += item + "\n";  	
			sb.append(item);
		}
		
		if (!Util.isEmpty(extraMsg)) {
			sb.append("<li><strong>" + extraMsg + "</li></strong>");
			textNotice += extraMsg + "\n";  
		}
		if (columns.size()>0
				|| !Util.isEmpty(extraMsg)) 
			sb.append("</ul>\n")
			.append("<HR>\n");
		
		return sb.toString();
	}	//getMessageColumnsChanged
	
	/**
	 * Get columns static on a message
	 * @return
	 */
	private String getMessageColumnsStatic() {
		
		StringBuffer sb = new StringBuffer();
		String result = "";
		if (entity!=null) {
			for (String column : ProjectProcessorService.RESPONSIBLE_COLUMNS) {
				if (entity.get_ColumnIndex(column) >= 0)
					sb.append(getItemPO(column,null));
			}
			
			for (String column : ProjectProcessorService.INFO_COLUMNS) {
				if (entity.get_ColumnIndex(column) >= 0)
					sb.append(getItemPO(column,null));
			}
			
			for (String column : ProjectProcessorService.TIME_COLUMNS) {
				if (entity.get_ColumnIndex(column) >= 0)
					sb.append(getItemPO(column,null));
			}
			if (sb.length()>0) {
				result = "<ul>\n " + sb.toString() + "</ul>\n <HR>\n ";
			}
		}
		return result;
	}	//getMessageColumnsStatic
	
	/**
	 * Get item from PO
	 * @param column
	 * @param value
	 * @return
	 */
	private String getItemPO(String column,String value) {
		
		StringBuffer result = new StringBuffer();
		
		result.append("<li><strong>" + Msg.parseTranslation(entity.getCtx(), "@" + column + "@") + ":</strong> ");
		if (value !=null)
			result.append(value);
		else 
			result.append(ProjectProcessorService.getDisplayValue(column,entity));
		
		result.append("</li>\n");

		return result.toString();
	}	//getItemPO
	
	/**
	 * Clear globals variables
	 */
	private void clearGlobals() {
		prefixSubject = "";
		prefixTextMail = "";
		columns.clear();
		values.clear();
		entity = null;
		project = null;
		projectPhase = null;
		projectTask = null;
		extraMsg = "";
		textNotice = "";
		isScheduled = false;
	}	//clearGlobals
	
}	

class ScheduleChange{
	
	private Properties ctx = null;
	private String trxName = null;
	private String tableName = null;
	private String whereClause = null;
	private String [] columnsToSet = new String [] {};
	private Object [] valuesToSet = new Object [] {};
	private Object [] parameters  = new Object [] {};
	private StringBuffer message = new StringBuffer();
	private boolean nextProjectStatus = false;
	
	/**
	 * Constructor
	 * @param ctx
	 * @param tableName
	 * @param whereClause
	 * @param trxName
	 */
	public ScheduleChange(Properties ctx, String tableName,String whereClause,String trxName) {
		this.ctx = ctx;
		this.tableName = tableName;
		this.whereClause = whereClause;
		this.trxName = trxName;
	}
	
	/**
	 * Set Table Name
	 * @param tableName
	 * @return
	 */
	public ScheduleChange setTableName(String tableName) {
		this.tableName = tableName;
		return this;
	}
	
	/**
	 * Set Where Clause
	 * @param whereClause
	 * @return
	 */
	public ScheduleChange setWhereClause(String whereClause) {
		this.whereClause = whereClause;
		return this;
	}
	
	/**
	 * Set Columns
	 * @param columns
	 * @return
	 */
	public ScheduleChange setColumns(String... columns) {
		this.columnsToSet = columns;
		return this;
	}
	
	/**
	 * Set Values
	 * @param values
	 * @return
	 */
	public ScheduleChange setValues(Object... values) {
		this.valuesToSet = values;
		return this;
	}
	
	/**
	 * Set Parameters from array
	 * @param parameters
	 * @return
	 */
	public ScheduleChange setParameters(Object... parameters) {
		this.parameters = parameters;
		return this;
	}
	
	/**
	 * Set Parameters from List Object
	 * @param parameters
	 * @return
	 */
	public ScheduleChange setParameters(List<Object> parameters) {
		this.parameters = parameters.toArray();
		return this;
	}
	
	/**
	 * Set Alert Message
	 * @param message
	 * @return
	 */
	public ScheduleChange setAlertMessageColumn(String message) {
		this.message.append(Msg.parseTranslation(ctx,message));
		return this;
	}
	
	/**
	 * if Changed Status
	 * @return
	 */
	public ScheduleChange IsNextProjectStatus() {
		this.nextProjectStatus = true;
		return this;
	}
	
	/**
	 * Process Schedule Changes
	 */
	public void processScheduleChanges() {
		List<PO> entitys = new Query(ctx, tableName, whereClause, trxName)
				.setParameters(parameters)
				.list();
		for (PO entity : entitys) {
			for (int i=0;i<columnsToSet.length;i++) 
				entity.set_ValueOfColumn(columnsToSet[i], valuesToSet[i]);
			
			if (nextProjectStatus) {
				if (entity.get_ColumnIndex(MProjectStatus.COLUMNNAME_C_ProjectStatus_ID)>=0) {
					int projecStatusID = entity.get_ValueAsInt(MProjectStatus.COLUMNNAME_C_ProjectStatus_ID);
					if (projecStatusID > 0) {
						MProjectStatus pStatus = MProjectStatus.get(ctx, entity.get_ValueAsInt(MProjectStatus.COLUMNNAME_C_ProjectStatus_ID));
						entity.set_ValueOfColumn(MProjectStatus.COLUMNNAME_C_ProjectStatus_ID, getNextStatus(pStatus));
					}
					
				}
			}
			
			if (this.message.length()>0) 
				entity.set_CustomColumn(MProject.COLUMNNAME_AlertMessage, this.message.toString());
			
			entity.save();
		}	
	}
	
	/**
	 * get Next Project Status 
	 * @param projectStatus
	 * @return
	 */
	private int getNextStatus(MProjectStatus projectStatus) {
		if (projectStatus.getTimeoutDays() <= 0
				|| projectStatus.getNext_Status_ID() == 0)
			return projectStatus.getC_ProjectStatus_ID();
		
		return projectStatus.getNext_Status_ID();
	}
}
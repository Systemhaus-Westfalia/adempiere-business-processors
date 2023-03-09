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
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;

import org.adempiere.core.domains.models.I_R_Request;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MChangeRequest;
import org.compiere.model.MClient;
import org.compiere.model.MGroup;
import org.compiere.model.MMailText;
import org.compiere.model.MRequest;
import org.compiere.model.MRequestProcessor;
import org.compiere.model.MRequestProcessorLog;
import org.compiere.model.MRequestProcessorRoute;
import org.compiere.model.MStatus;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;
import org.compiere.util.Util;
import org.spin.model.MRNoticeTemplate;
import org.spin.model.MRNoticeTemplateEvent;
import org.spin.queue.notification.DefaultNotifier;
import org.spin.queue.util.QueueLoader;

/** 
 * 	Generated Process for (Product Internal Use)
 *  @author Jorg Janke
 *  @version $Id: RequestProcessor.java,v 1.3 2006/07/30 00:53:33 jjanke Exp $
 *  @version Release 3.9.3
 */
public class RequestProcessor extends RequestProcessorAbstract {
	
	/** Client onfo					*/
	private MClient 			client = null;
	/**	The Concrete Model			*/
	private MRequestProcessor	requestProcessor = null;
	/**	Last Summary				*/
	private StringBuffer 		summary = new StringBuffer();
	/**	Count						*/
	private int					count = 0;
	/**	Mail Count					*/
	private int 				mailCount = 0;
	/**	Initial work time			*/
	private long 				startWork;
	
	@Override
	protected void prepare() {
		super.prepare();
		if(getRequestProcessorId() <= 0) {
			throw new AdempiereException("@R_RequestProcessor_ID@ @NotFound@");
		}
		requestProcessor = new MRequestProcessor(getCtx(), getRequestProcessorId(), get_TrxName());
		client = MClient.get(getCtx(), requestProcessor.getAD_Client_ID());
	}

	@Override
	protected String doIt() throws Exception {
		startWork = System.currentTimeMillis();
		summary = new StringBuffer();
		//
		processEMail();
		findSalesRep ();
		processStatus();
		processRequests ();
		processECR();
		//
		int no = requestProcessor.deleteLog();
		summary.append("Logs deleted=").append(no);
		if (requestProcessor.get_TrxName() == null) {
			addRequestProcessorLog(requestProcessor.get_TrxName());
		} else {
			Trx.run(this::addRequestProcessorLog);
		}
		return TimeUtil.formatElapsed(new Timestamp(startWork));
	}
	
	/**
	 * Add Request Processor Log
	 * @param trxName
	 */
	private void addRequestProcessorLog(String trxName) {
		MRequestProcessorLog requestProcessorLog = new MRequestProcessorLog(requestProcessor, summary.toString(), trxName);
		requestProcessorLog.setReference(TimeUtil.formatElapsed(new Timestamp(startWork)));
		requestProcessorLog.saveEx();
	}

	/**************************************************************************
	 *  Process requests.
	 *  Scheduled - are they due?
	 */
	private void processRequests ()
	{
		resetCounter();
		StringBuffer whereClause = new StringBuffer();
		List<Object> parameters = new ArrayList<>();
		/**
		 *  Due Requests
		 */
		resetCounter();
		whereClause = new StringBuffer("DueType = ? AND Processed = 'N' AND AD_Client_ID = ?");
		parameters = new ArrayList<>();
		parameters.add(MRequest.DUETYPE_Scheduled);
		parameters.add(requestProcessor.getAD_Client_ID());
		//	
		if (requestProcessor.getR_RequestType_ID() != 0) {
			whereClause.append(" AND R_RequestType_ID = ?");
			parameters.add(requestProcessor.getR_RequestType_ID());
		}
		whereClause.append(" AND DateNextAction < SysDate");
		//	Query for request
		new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
			.setParameters(parameters)
			.<MRequest>list()
			.forEach(request -> {
				request.setDueType();
				if (request.isDue()) {
					if (request.getRequestType().isEMailWhenDue()) {
						if (sendEmail (request, MRNoticeTemplateEvent.EVENTTYPE_SalesRepDueRequestAlert)) {
							request.setDateLastAlert();
							addMailCount();
						}
					}
					request.saveEx();
					addRecordCount();
				}
			});
		summary.append("New Due #").append(count);
		if (mailCount > 0)
			summary.append(" (").append(mailCount).append(" EMail)");
		summary.append (" - ");
		
		/**
		 *  Overdue Requests.
		 *  Due Requests - are they overdue?
		 */
		resetCounter();
		whereClause = new StringBuffer("DueType = ? AND Processed = 'N' AND AD_Client_ID = ?");
		parameters = new ArrayList<>();
		parameters.add(MRequest.DUETYPE_Due);
		parameters.add(requestProcessor.getAD_Client_ID());
		//	
		if (requestProcessor.getR_RequestType_ID() != 0) {
			whereClause.append(" AND R_RequestType_ID = ?");
			parameters.add(requestProcessor.getR_RequestType_ID());
		}
		whereClause.append(" AND EXISTS(SELECT 1 FROM R_RequestType rt "
				+ "WHERE R_Request.R_RequestType_ID = rt.R_RequestType_ID"
				+ " AND (R_Request.DateNextAction+rt.DueDateTolerance) < SysDate)");
		//	Query for request
		new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
			.setParameters(parameters)
			.<MRequest>list()
			.forEach(request -> {
				request.setDueType();
				if (request.isOverdue()) {
					if (request.getRequestType().isEMailWhenOverdue()
						&& !TimeUtil.isSameDay(request.getDateLastAlert(), null)) {
						if (sendEmail (request, MRNoticeTemplateEvent.EVENTTYPE_AutomaticTaskExpiredTaskAlert)) {
							request.setDateLastAlert();
							addMailCount();
						}
					}
					request.saveEx();
					addRecordCount();
				}
			});
		summary.append("New Overdue #").append(count);
		if (mailCount > 0)
			summary.append(" (").append(mailCount).append(" EMail)");
		summary.append (" - ");
		
		/**
		 *  Send (over)due alerts
		 */
		if (requestProcessor.getOverdueAlertDays() > 0) {
			resetCounter();
			whereClause = new StringBuffer("Processed = 'N' AND AD_Client_ID = ?");
			parameters = new ArrayList<>();
			parameters.add(requestProcessor.getAD_Client_ID());
			whereClause.append(" AND (DateNextAction+" + requestProcessor.getOverdueAlertDays() + ") < SysDate"
					+ " AND (DateLastAlert IS NULL");
			if (requestProcessor.getRemindDays() > 0) {
				whereClause.append(" OR (DateLastAlert+" + requestProcessor.getRemindDays() + ") < SysDate");
			}
			whereClause.append(")");
			//	
			if (requestProcessor.getR_RequestType_ID() != 0) {
				whereClause.append(" AND R_RequestType_ID = ?");
				parameters.add(requestProcessor.getR_RequestType_ID());
			}
			//	Query for request
			new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.<MRequest>list()
				.forEach(request -> {
					request.setDueType();
					if (request.getRequestType().isEMailWhenOverdue()
							&& (request.getDateLastAlert() == null
								|| !TimeUtil.isSameDay(request.getDateLastAlert(), null))){
						if (sendEmail (request, MRNoticeTemplateEvent.EVENTTYPE_AutomaticTaskExpiredTaskAlert)) {
							request.setDateLastAlert();
							addMailCount();
						}
						request.saveEx();
						addRecordCount();
					}
				});
			summary.append("Alerts #").append(count);
			if (mailCount > 0)
				summary.append(" (").append(mailCount).append(" EMail)");
			summary.append (" - ");
		}	//	Overdue
		
		/**
		 *  Escalate
		 */
		if (requestProcessor.getOverdueAssignDays() > 0) {
			resetCounter();
			whereClause = new StringBuffer("Processed = 'N' AND AD_Client_ID = ? AND IsEscalated='N'");
			parameters = new ArrayList<>();
			parameters.add(requestProcessor.getAD_Client_ID());
			whereClause.append(" AND (DateNextAction+" + requestProcessor.getOverdueAssignDays() + ") < SysDate");
			//	
			if (requestProcessor.getR_RequestType_ID() != 0) {
				whereClause.append(" AND R_RequestType_ID = ?");
				parameters.add(requestProcessor.getR_RequestType_ID());
			}
			//	Query for request
			new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.<MRequest>list()
				.forEach(request -> {
					if(escalate(request)) {
						addRecordCount();
					}
				});
			summary.append("Escalated #").append(count).append(" - ");
		}	//	Esacalate
		
		/**
		 *  Send inactivity alerts
		 */
		if (requestProcessor.getInactivityAlertDays() > 0)
		{
			resetCounter();
			whereClause = new StringBuffer("Processed = 'N' AND AD_Client_ID = ?");
			parameters = new ArrayList<>();
			parameters.add(requestProcessor.getAD_Client_ID());
			whereClause.append(" AND (Updated+" + requestProcessor.getInactivityAlertDays() + ") < SysDate AND (DateLastAlert IS NULL");
			if (requestProcessor.getRemindDays() > 0) {
				whereClause.append(" OR (DateLastAlert+" + requestProcessor.getRemindDays() + ") < SysDate");
			}
			whereClause.append(")");
			//	
			if (requestProcessor.getR_RequestType_ID() != 0) {
				whereClause.append(" AND R_RequestType_ID = ?");
				parameters.add(requestProcessor.getR_RequestType_ID());
			}
			//	Query for request
			new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
				.setParameters(parameters)
				.<MRequest>list()
				.forEach(request -> {
					if (request.getDateLastAlert() == null
							|| !TimeUtil.isSameDay(request.getDateLastAlert(), null)) {
						if (sendEmail (request, MRNoticeTemplateEvent.EVENTTYPE_AutomaticTaskExpiredTaskAlert)) {
							request.setDateLastAlert();
							addMailCount();
						}
						request.saveEx();
						addRecordCount();
					}
				});
			summary.append("Inactivity #").append(count).append(" - ");
			if (mailCount > 0)
				summary.append(" (").append(mailCount).append(" EMail)");
			summary.append (" - ");
		}	//	Inactivity		
	}	//  processRequests

	/**
	 *  Send Alert EMail
	 *  @param request request
	 *  @param eventType Event Type
	 *  @return true if sent
	 */
	private boolean sendEmail (MRequest request, String eventType) {
		AtomicReference<String> subject = new AtomicReference<String>();
		AtomicReference<String> message = new AtomicReference<String>();
		//	Event Type
		if(!Util.isEmpty(eventType)) {
			MMailText mailText = MRNoticeTemplate.getMailTemplate(getCtx(), MRNoticeTemplate.TEMPLATETYPE_Request, eventType);
			if(mailText != null) {
				//	Add Request
				mailText.setPO(request, true);
				subject.set(mailText.getMailHeader());
				//	Message
				message.set(mailText.getMailText(true));
			}
		}
		if(Util.isEmpty(subject.get())
				&& Util.isEmpty(message.get())) {
			//  Alert: Request {0} overdue
			subject.set(Msg.getMsg(client.getAD_Language(), "RequestDue", 
				new String[] {request.getDocumentNo()}));
			message.set(request.getSummary());
		}
		//	
		Trx.run(transactionName -> {
			//	Get instance for notifier
			try {
				DefaultNotifier notifier = (DefaultNotifier) QueueLoader.getInstance().getQueueManager(DefaultNotifier.QUEUETYPE_DefaultNotifier)
						.withContext(getCtx())
						.withTransactionName(transactionName);
				//	Send notification to queue
				notifier
					.clearMessage()
					.withApplicationType(DefaultNotifier.DefaultNotificationType_UserDefined)
					.withUserId(request.getUpdatedBy())
					.addRecipient(request.getSalesRep_ID())
					.withText(message.get())
					.addAttachment(request.createPDF())
					.withDescription(subject.get());
				//	Add to queue
				notifier.addToQueue();
			} catch (Exception e) {
				log.severe(e.getLocalizedMessage());
			}
		});
		return true;
	}   //  sendAlert

	/**
	 *  Escalate
	 *  @param request request
	 * 	@return true if saved
	 */
	private boolean escalate (MRequest request)
	{
		//  Get Supervisor
		MUser supervisor = request.getSalesRep();	//	self
		int supervisor_ID = request.getSalesRep().getSupervisor_ID();
		if (supervisor_ID == 0 && requestProcessor.getSupervisor_ID() != 0)
			supervisor_ID = requestProcessor.getSupervisor_ID();
		if (supervisor_ID != 0 && supervisor_ID != request.getAD_User_ID())
			supervisor = MUser.get(getCtx(), supervisor_ID);
		
		//  Escalated: Request {0} to {1}
		AtomicReference<String> subject = new AtomicReference<String>();
		AtomicReference<String> message = new AtomicReference<String>();
		//	Event Type
		MMailText mailText = MRNoticeTemplate.getMailTemplate(getCtx(), MRNoticeTemplate.TEMPLATETYPE_Request, MRNoticeTemplateEvent.EVENTTYPE_AutomaticTaskTaskTransferNotice);
		if(mailText != null) {
			//	Add Request
			mailText.setPO(request, true);
			subject.set(mailText.getMailHeader());
			//	Message
			message.set(mailText.getMailText(true));
		}
		if(Util.isEmpty(subject.get())
				&& Util.isEmpty(message.get())) {
			//  Alert: Request {0} overdue
			subject.set(Msg.getMsg(client.getAD_Language(), "RequestEscalate", new String[] {request.getDocumentNo(), supervisor.getName()}));
			message.set(request.getSummary());
		}
		List<Integer> recipients = new ArrayList<>();
		recipients.add(request.getSalesRep_ID());
		//	Not the same - send mail to supervisor
		if (request.getSalesRep_ID() != supervisor.getAD_User_ID()) {
			recipients.add(supervisor.getAD_User_ID());
		}
		//	
		Trx.run(transactionName -> {
			//	Get instance for notifier
			DefaultNotifier notifier = (DefaultNotifier) QueueLoader.getInstance().getQueueManager(DefaultNotifier.QUEUETYPE_DefaultNotifier)
					.withContext(getCtx())
					.withTransactionName(transactionName);
			//	Send notification to queue
			notifier
				.clearMessage()
				.withApplicationType(DefaultNotifier.DefaultNotificationType_UserDefined)
				.withText(message.get())
				.addAttachment(request.createPDF())
				.withDescription(subject.get());
			//	Add recipients
			recipients.forEach(recipientId -> notifier.addRecipient(recipientId));
			//	Add to queue
			notifier.addToQueue();
		});
		//  ----------------
		request.setDueType();
		request.setIsEscalated(true);
		request.setResult(subject.get());
		return request.save();
	}   //  escalate

	/**
	 * Add Record count
	 */
	private void addRecordCount() {
		count++;
	}
	
	/**
	 * Add mail count
	 */
	private void addMailCount() {
		mailCount++;
	}
	
	/**
	 * Reset Counter
	 */
	private void resetCounter() {
		count = 0;
		mailCount = 0;
	}

	/**************************************************************************
	 * 	Process Request Status
	 */
	private void processStatus()
	{
		resetCounter();
		StringBuffer whereClause = new StringBuffer("EXISTS("
				+ "SELECT 1 FROM R_Status s "
				+ "WHERE R_Request.R_Status_ID=s.R_Status_ID"
				+ " AND s.TimeoutDays > 0 AND s.Next_Status_ID > 0"
				+ " AND R_Request.DateLastAction+s.TimeoutDays < SysDate"
				+ ")");
		//	Query for request
		new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
			.setOrderBy(I_R_Request.COLUMNNAME_R_Status_ID)
			.<MRequest>list()
			.stream()
			.filter(request -> MStatus.get(getCtx(), request.getR_Status_ID()).getTimeoutDays() > 0 
					&& MStatus.get(getCtx(), request.getR_Status_ID()).getNext_Status_ID() > 0)
			.forEach(request -> {
				//	Get/Check Status
				MStatus status = MStatus.get(getCtx(), request.getR_Status_ID());
				//	Next Status
				MStatus next = MStatus.get(getCtx(), status.getNext_Status_ID());
				//
				String result = Msg.getMsg(getCtx(), "RequestStatusTimeout")
					+ ": " + status.getName() + " -> " + next.getName();
				request.setResult(result);
				request.setR_Status_ID(status.getNext_Status_ID());
				if(request.save()) {
					addRecordCount();
				}
			});	
		summary.append("Status Timeout #").append(count)
			.append(" - ");
	}	//	processStatus
	
	/**
	 * 	Create ECR
	 */
	private void processECR()
	{
		//	Get Requests with Request Type-AutoChangeRequest and Group with info
		resetCounter();
		StringBuffer whereClause = new StringBuffer("M_ChangeRequest_ID IS NULL"
			+ " AND EXISTS("
				+ "SELECT 1 FROM R_RequestType rt "
				+ "WHERE rt.R_RequestType_ID = R_Request.R_RequestType_ID"
				+ " AND rt.IsAutoChangeRequest='Y')"
			+ "AND EXISTS ("
				+ "SELECT 1 FROM R_Group g "
				+ "WHERE g.R_Group_ID = R_Request.R_Group_ID"
				+ " AND (g.M_BOM_ID IS NOT NULL OR g.M_ChangeNotice_ID IS NOT NULL)	)");
		//	Query for request
		new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
			.setOrderBy(I_R_Request.COLUMNNAME_R_Status_ID)
			.<MRequest>list()
			.forEach(request -> {
				MGroup requestGroup = MGroup.get(getCtx(), request.getR_Group_ID());
				MChangeRequest changeRequest = new MChangeRequest(request, requestGroup);
				if (request.save()) {
					request.setM_ChangeRequest_ID(changeRequest.getM_ChangeRequest_ID());
					if (request.save()) {
						addMailCount();
					}
				}
				addRecordCount();
			});
		summary.append("Auto Change Request #").append(count);
		if ((count - mailCount) > 0)
			summary.append("(fail=").append((count - mailCount)).append(")");
		summary.append(" - ");
	}	//	processECR
	
	
	/**************************************************************************
	 *	Create Reauest / Updates from EMail
	 */
	private void processEMail ()
	{
	//	m_summary.append("Mail #").append(count)
	//		.append(" - ");
	}   //  processEMail

	
	/**************************************************************************
	 * 	Allocate Sales Rep
	 */
	private void findSalesRep () {
		StringBuffer whereClause = new StringBuffer("Processed = 'N' AND AD_Client_ID = ? AND (SalesRep_ID = 0 OR SalesRep_ID IS NULL)");
		List<Object> parameters = new ArrayList<>();
		parameters.add(requestProcessor.getAD_Client_ID());
		resetCounter();
		//	Query for request
		new Query(getCtx(), I_R_Request.Table_Name, whereClause.toString(), null)
			.setParameters(parameters)
			.<MRequest>list()
			.forEach(request -> {
				int salesRepId = findSalesRep(request);
				if (salesRepId != 0) {
					request.setSalesRep_ID(salesRepId);
					if(request.save()) {
						addMailCount();
					}
				}
				addRecordCount();
			});
		//
		if (mailCount == 0 && (count - mailCount) == 0)
			summary.append("No unallocated Requests");
		else
			summary.append("Allocated SalesRep=").append(mailCount);
		if ((count - mailCount) > 0)
			summary.append(",Not=").append((count - mailCount));
		summary.append(" - ");
	}	//	findSalesRep

	/**
	 *  Find SalesRep/User based on Request Type and Question.
	 *  @param request request
	 *  @return SalesRep_ID user
	 */
	private int findSalesRep (MRequest request)
	{
		String QText = request.getSummary();
		if (QText == null)
			QText = "";
		else
			QText = QText.toUpperCase();
		//
		MRequestProcessorRoute[] routes = requestProcessor.getRoutes(false);
		for (int i = 0; i < routes.length; i++) {
			MRequestProcessorRoute route = routes[i];
			
			//	Match first on Request Type
			if (request.getR_RequestType_ID() == route.getR_RequestType_ID()
				&& route.getR_RequestType_ID() != 0)
				return route.getAD_User_ID();
			
			//	Match on element of keyword
			String keyword = route.getKeyword();
			if (keyword != null)
			{
				StringTokenizer st = new StringTokenizer(keyword.toUpperCase(), " ,;\t\n\r\f");
				while (st.hasMoreElements()) {
					if (QText.indexOf(st.nextToken()) != -1)
						return route.getAD_User_ID();
				}
			}
		}	//	for all routes

		return requestProcessor.getSupervisor_ID();
	}   //  findSalesRep
	
	/**
	 * 	Get Server Info
	 *	@return info
	 */
	public String getServerInfo()
	{
		return "Last=" + summary.toString();
	}	//	getServerInfo
}
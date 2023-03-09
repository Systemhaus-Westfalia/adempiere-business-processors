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
import java.util.Optional;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.acct.SessionPoster;
import org.compiere.model.MAcctProcessor;
import org.compiere.model.MAcctProcessorLog;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAcctSchemaProvider;
import org.compiere.util.TimeUtil;
import org.compiere.util.Trx;

/** 
 * 	Generated Process for (Product Internal Use)
 *  @author Jorg Janke
 *  @version $Id: RequestProcessor.java,v 1.3 2006/07/30 00:53:33 jjanke Exp $
 *  @version Release 3.9.3
 */
public class AcctProcessor extends AcctProcessorAbstract {
	
	private MAcctProcessor acctProcessor = null;
    private MAcctSchemaProvider provider = new MAcctSchemaProvider();
    private SessionPoster sessionPoster = new SessionPoster();
    private StringBuilder summary = new StringBuilder("Just initialized ");
	/**	Initial work time			*/
	private long 				startWork;
	
	@Override
	protected void prepare() {
		super.prepare();
		if(getAcctProcessorId() <= 0) {
			throw new AdempiereException("@C_AcctProcessor_ID@ @NotFound@");
		}
		acctProcessor = new MAcctProcessor(getCtx(), getAcctProcessorId(), get_TrxName());
	}

	@Override
	protected String doIt() throws Exception {
		startWork = System.currentTimeMillis();
		//
		String results = getSessionPoster()
                .withAccountingSchemas(getAcctSchemas())
                .post();

        updateLog(results);
		return TimeUtil.formatElapsed(new Timestamp(startWork));
	}
	
	MAcctSchemaProvider getAcctSchemaProvider() {

        return provider;

    }

    private MAcctSchema[] getAcctSchemas() {
    
        return getAcctSchemaProvider().getAcctSchemas(getCtx(),
                getAD_Client_ID(),
                getC_AcctSchema_ID(), null);
    
    }

    int getC_AcctSchema_ID() {

        return acctProcessor.getC_AcctSchema_ID();

    }

    /**
     * Get Server Info
     * 
     * @return info
     */
    public String getServerInfo() {
    
        return "Last=" + Optional.ofNullable(summary.toString()).orElse("");
    
    }

    SessionPoster getSessionPoster() {
    
        return sessionPoster;
    
    }

    void updateLog(String results) {
        
        summary = new StringBuilder(results);
        
        int no = acctProcessor.deleteLog();
        summary.append("Logs deleted=").append(no);
        if (acctProcessor.get_TrxName() == null) {
            Trx.run(this::addAcctProcessorLog);
        } else {
            addAcctProcessorLog(acctProcessor.get_TrxName());
        }
    }

    /**
     * Add Acct Processor Log
     * @param trxName
     */
    private void addAcctProcessorLog(String trxName) {
        MAcctProcessorLog acctProcessorLog = new MAcctProcessorLog(acctProcessor, summary.toString(), trxName);
        acctProcessorLog.setReference(TimeUtil.formatElapsed(new Timestamp(startWork)));
        acctProcessorLog.saveEx();
    }
}
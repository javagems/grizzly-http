/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */

/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.sun.grizzly.http;


import com.sun.grizzly.arp.AsyncHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.ActionHook;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Processor;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.RequestGroupInfo;
import com.sun.grizzly.tcp.RequestInfo;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.InputFilter;
import com.sun.grizzly.tcp.http11.OutputFilter;
import com.sun.grizzly.tcp.http11.filters.ChunkedInputFilter;
import com.sun.grizzly.tcp.http11.filters.ChunkedOutputFilter;
import com.sun.grizzly.tcp.http11.filters.GzipOutputFilter;
import com.sun.grizzly.tcp.http11.filters.IdentityInputFilter;
import com.sun.grizzly.tcp.http11.filters.IdentityOutputFilter;
import com.sun.grizzly.tcp.http11.filters.VoidInputFilter;
import com.sun.grizzly.tcp.http11.filters.VoidOutputFilter;
import com.sun.grizzly.tcp.http11.filters.BufferedInputFilter;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.Interceptor;

import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.buf.Ascii;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.HexUtils;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.FastHttpDateFormat;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.SSLSupport;

import com.sun.grizzly.util.res.StringManager;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.management.ObjectName;

/**
 * Process HTTP request. This class is based on
 * <code>com.sun.grizzly.tcp.http11.Http11Processor</code>
 *
 * @author Jean-Francois Arcand
 */
public class ProcessorTask extends TaskBase implements Processor, 
        ActionHook {

    private final static Logger logger = SelectorThread.logger();

    private boolean isSecurityEnabled;
    
    
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);
    
    
    /**
     * Associated adapter.
     */
    protected Adapter adapter = null;


    /**
     * Request object.
     */
    protected Request request = null;


    /**
     * Response object.
     */
    protected Response response = null;


    /**
     * Input.
     */
    protected InternalInputBuffer inputBuffer = null;

    
    /**
     * Input Stream.
     */
    protected InputStream inputStream = null;
    
    
    /**
     * Output Stream.
     */
    protected OutputStream outputStream = null;

    
    /**
     * Output.
     */
    protected SocketChannelOutputBuffer outputBuffer = null;


    /**
     * State flag.
     */
    protected boolean started = false;


    /**
     * Error flag.
     */
    protected boolean error = false;


    /**
     * Keep-alive.
     */
    protected boolean keepAlive = true;
    
    
    /**
     * Connection: value
     */
    protected boolean connectionHeaderValueSet = false;

    
    /**
     * HTTP/1.1 flag.
     */
    protected boolean http11 = true;


    /**
     * HTTP/0.9 flag.
     */
    protected boolean http09 = false;


    /**
     * Content delimitator for the request (if false, the connection will
     * be closed at the end of the request).
     */
    protected boolean contentDelimitation = true;


    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;


    /**
     * Socket associated with the current connection.
     */
    protected Socket socket;


    /**
     * Remote Address associated with the current connection.
     */
    protected String remoteAddr = null;


    /**
     * Remote Host associated with the current connection.
     */
    protected String remoteHost = null;
    
    
    /**
     * Local Host associated with the current connection.
     */
    protected String localName = null;
        
    
    /**
     * Local port to which the socket is connected
     */
    protected int localPort = -1;
    
    
    /**
     * Remote port to which the socket is connected
     */
    protected int remotePort = -1;
    
    
    /**
     * The local Host address.
     */
    protected String localAddr = null; 


    /**
     * Maximum uploadTimeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int uploadTimeout = 300000;


    /**
     * Max post size.
     */
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * Host name (used to avoid useless B2C conversion on the host name).
     */
    protected char[] hostNameC = new char[0];
    
    
    /**
     * Has the request associated with this {@link ProcessorTask} been
     * registered with the {@link RequestGroupInfo}
     */
    protected boolean hasRequestInfoRegistered = false;
    
    
    /**
     * Default HTTP header buffer size.
     */
    protected int maxHttpHeaderSize = Constants.DEFAULT_HEADER_SIZE;

    
    /**
     * The number of requests {@link ProcessorTask} has proceeded.
     */
    protected static int requestCount;

    
    /**
     * The input request buffer size.
     */
    protected int requestBufferSize = Constants.DEFAULT_REQUEST_BUFFER_SIZE;


    /**
     * ObjectName under which this {@link ProcessorTask} will be
     * JMX-registered if monitoring has been turned on
     */
    protected ObjectName oname;
    
    
    /**
     * Allow client of this class to force connection closing.
     */
    protected boolean dropConnection = false;
    
    
    /**
     * The current keep-alive count left before closing the connection.
     */
    protected int keepAliveLeft;
  
     
    /**
     * The handler used by this {@link Task} to manipulate the request.
     */
    protected Interceptor handler;
    
    
    /**
     * The default response-type
     */
    protected String defaultResponseType = Constants.DEFAULT_RESPONSE_TYPE;
     
    
    /**
     * The forced request-type
     */
    protected String forcedRequestType = Constants.FORCED_REQUEST_TYPE;     
    
    
    /**
     * Is asynchronous mode enabled?
     */
    protected boolean asyncExecution = false;
    
    
    /**
     * The code>RequestInfo</code> used to gather stats.
     */
    protected RequestInfo requestInfo;
    
    
    /**
     * When the asynchronous mode is enabled, the execution of this object
     * will be delegated to the {@link AsyncHandler}
     */
    protected AsyncHandler asyncHandler;

    
    private Semaphore asyncSemaphore = new Semaphore(1);
    
// ----------------------------------------------- Compression Support ---//


    /**
     * List of user agents to not use gzip with
     */
    protected String[] noCompressionUserAgents = null;


    /**
     * List of MIMES which could be gzipped
     */
    protected String[] compressableMimeTypes 
            = { "text/html", "text/xml", "text/plain" };
    
   
    /**
     * Allowed compression level.
     */
    protected int compressionLevel = 0;


    /**
     * Minimum contentsize to make compression.
     */
    protected int compressionMinSize = 2048;
    
    
    /**
     * List of restricted user agents.
     */
    protected String[] restrictedUserAgents = null;

        
    /**
     * Buffer the response until the buffer is full.
     */
    protected boolean bufferResponse = true;
    
        
    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = true;


    /**
     * Flag, which indicates if async HTTP write is enabled
     */
    protected boolean isAsyncHttpWriteEnabled;    
    
    
    /**
     * The maximum time a connection can stay open holding a {@link WorkerThread}. 
     * Default is 5 minutes like Apache.
     */
    private int transactionTimeout = Constants.DEFAULT_TIMEOUT;
    
    /**
     * Use chunking.
     */
    private boolean useChunking = true;


    private final static String USE_KEEP_ALIVE =
                "com.sun.grizzly.useKeepAliveAlgorithm";

    private final static String BLOCKING_KEEP_ALIVE =
                "com.sun.grizzly.keepAliveLockingThread";

    private boolean useKeepAliveAlgorithm = true;

    /**
     * Max keep-alive request before timing out.
     */
    protected int maxKeepAliveRequests = Constants.DEFAULT_MAX_KEEP_ALIVE;
   
    // When true, a Thread will be reserved to serve requests for the keep-alive
    // being time.
    protected boolean handleKeepAliveBlockingThread = false;


    // ----------------------------------------------------- Constructor ---- //

    public ProcessorTask(){
        this(true);
    }
       
    
    public ProcessorTask(boolean init){    
        type = PROCESSOR_TASK;
        if (init) {
            initialize();
        }
    }
    
    
    public ProcessorTask(boolean init, boolean bufferResponse){    
        this.bufferResponse = bufferResponse;

        type = PROCESSOR_TASK;
        if (init) {
            initialize();
        }
    }
    
    
    /**
     * Initialize the stream and the buffer used to parse the request.
     */
    public void initialize(){
        isSecurityEnabled = (System.getSecurityManager() != null);
        started = true;   
        request = new Request();

        response = new Response();
        response.setHook(this);
        
        inputBuffer = new InternalInputBuffer(request,requestBufferSize); 
        
        outputBuffer = new SocketChannelOutputBuffer(response, 
                                                     maxHttpHeaderSize,
                                                     bufferResponse);           
        

        request.setInputBuffer(inputBuffer);
       
        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        initializeFilters();

        if (System.getProperty(USE_KEEP_ALIVE) != null) {
            useKeepAliveAlgorithm =
                        Boolean.valueOf(
                            System.getProperty(USE_KEEP_ALIVE)).booleanValue();

            if (!useKeepAliveAlgorithm)
                logger.info("Keep Alive algorith will no be used");
        }

        if (System.getProperty(BLOCKING_KEEP_ALIVE) != null) {
            handleKeepAliveBlockingThread =
                        Boolean.valueOf(
                            System.getProperty(BLOCKING_KEEP_ALIVE)).booleanValue();

            if (!handleKeepAliveBlockingThread)
                logger.info("Keep Alive blocking thread algorithm will no be used");
        }   
    }


    // ----------------------------------------------------- Thread run ---- //
    
    
     /**
     * Execute the HTTP request by parsing the header/body,
     * and then by delegating the process to the Catalina container.
     */
    public void doTask() throws IOException{
        try {
            process(inputStream,
                    outputStream);
        } catch(Throwable ex){
            logger.log(Level.FINE,
                    sm.getString("processorTask.errorProcessingRequest"), ex);
        } finally {
            terminateProcess();        
        }
    }

 
     // --------------------------------------------------------- TaskEvent ---// 
       
    
    /**
     * Pre process the request by decoding the request line and the header.
     */ 
    public void preProcess() throws Exception {
        preProcess(inputStream,outputStream);
    }
    
    
    /**
     * Pre process the request by decoding the request line and the header.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     */     
    public void preProcess(InputStream input, OutputStream output)
                                                            throws Exception {
        
        // Make sure this object has been initialized.
        if ( !started ){
            initialize();
        }
        // Setting up the I/O
        inputBuffer.setInputStream(input);
        if ( key != null ) {
            inputStream = (InputReader)input;
            outputBuffer.setAsyncHttpWriteEnabled(
                    isAsyncHttpWriteEnabled);
            outputBuffer.setAsyncQueueWriter(
                    selectorHandler.getAsyncQueueWriter());
            outputBuffer.setSelectionKey(key);
	     response.setChannel((SocketChannel)key.channel());
        } 
        configPreProcess();
    }
        
    
    /**
     * Prepare this object before parsing the request.
     */
    protected void configPreProcess() throws Exception {
                   
        if(selectorThread.isMonitoringEnabled() 
                && !hasRequestInfoRegistered ) {
            registerMonitoring();
        } else if (!selectorThread.isMonitoringEnabled() && hasRequestInfoRegistered) {
            unregisterMonitoring();
        } 
        
        if (selectorThread.isMonitoringEnabled()) {
            requestInfo = request.getRequestProcessor();
            requestInfo.setWorkerThreadID(Thread.currentThread().getId());
        }
        
        // Set the remote address
        remoteAddr = null;
        remoteHost = null;
        localName = null;
        localAddr = null;
        remotePort = -1;
        localPort = -1;
        connectionHeaderValueSet = false;
        
        // Error flag
        error = false;
        keepAlive = true;

        if (request.getServerPort() == 0) {
            request.setServerPort(selectorThread.getPort());
        }        
    }

    
    /**
     * Process an HTTP request using a non blocking {@link Socket}
     */      
    protected boolean doProcess() throws Exception {      
        if (!handleKeepAliveBlockingThread){
            boolean exitWhile = parseRequest();
            if ( exitWhile ) return exitWhile;
            invokeAdapter();
            postResponse();
        } else {
            int soTimeout = ((InputReader)inputStream).getReadTimeout();
            DefaultThreadPool st =(DefaultThreadPool) getThreadPool();
            if ( useKeepAliveAlgorithm ) {
               float threadRatio =
                    (float) st.getActiveCount()
                    / (float) st.getMaximumPoolSize();

                if ((threadRatio > 0.33) && (threadRatio <= 0.66)) {
                    soTimeout = soTimeout / 2;
                } else if (threadRatio > 0.66) {
                    soTimeout = soTimeout / 5;
                    keepAliveLeft = 1;
                }
                ((InputReader)inputStream).setReadTimeout(soTimeout);
            }

            while (started && !error && keepAlive) {
                boolean exitWhile = parseRequest();

                if (maxKeepAliveRequests > 0 && --keepAliveLeft == 0){
                    keepAlive = false;
                }
                if ( exitWhile ) return exitWhile;

                invokeAdapter();
                postResponse();
            }
        }
        return error;
    }

    
    /**
     * Prepare and post the response.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     */       
    public void postResponse() throws Exception{
        
        // Do not commit the response;
        if (response.isSuspended()){
            WorkerThread wt = (WorkerThread)Thread.currentThread();
            wt.getAttachment().setAttribute("suspend",Boolean.TRUE);

            ((SelectorThreadKeyHandler) selectorHandler.
                    getSelectionKeyHandler()).resetExpiration();
            key.attach(response.getResponseAttachment());
             
            return;
        }        
        
        if (selectorThread.isMonitoringEnabled()) {
            request.updateCounters();  
        }
        
        try {
            adapter.afterService(request,response);
        } catch (Exception ex) {
            error = true;
            logger.log(Level.FINEST,
                    sm.getString("processorTask.errorFinishingRequest"), ex);            
        }
        
        // Finish the handling of the request
        try {
            if (error){
                inputBuffer.setSwallowInput(false);
            }
            inputBuffer.endRequest();
        } catch (IOException e) {
            error = true;
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    sm.getString("processorTask.errorFinishingRequest"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            error = true;
        }
        try {
            outputBuffer.endRequest();
        } catch (IOException e) {
            error = true;
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    sm.getString("processorTask.errorFinishingResponse"), t);
            error = true;
        }

        // If there was an error, make sure the request is counted as
        // and error, and update the statistics counter
        if (error) {
            response.setStatus(500);
        }
        
        // Next request
        inputBuffer.nextRequest();
        outputBuffer.nextRequest();
    }
    
    
    /**
     * Invoke the {@link Adapter}, which usualy invoke the Servlet
     * Container.
     */
    public void invokeAdapter(){
        // Process the request in the adapter
        if (!error) {
            try {
                adapter.service(request, response);
                // Handle when the response was committed before a serious
                // error occurred.  Throwing a ServletException should both
                // set the status to 500 and set the errorException.
                // If we fail here, then the response is likely already 
                // committed, so we can't try and set headers.
                if(keepAlive && !error) { // Avoid checking twice.
                    error = response.getErrorException() != null ||
                            statusDropsConnection(response.getStatus());
                }
            } catch (InterruptedIOException e) {
                error = true;
            } catch (Throwable t) {
                logger.log(Level.SEVERE,
                        sm.getString("processorTask.serviceError"), t);
                // 500 - Internal Server Error
                response.setStatus(500);
                error = true;
            }
        }
    }
    
    
    /**
     * Parse the request line and the http header.
     */
    public boolean parseRequest() throws Exception {
        
        // Parsing the request header
        try { 

            inputBuffer.parseRequestLine();
            if (selectorThread.isMonitoringEnabled()) {
                request.getRequestProcessor().setRequestCompletionTime(0);
            }

            request.setStartTime(System.currentTimeMillis());
            if ( handler != null && 
                    handler.handle(request,Interceptor.REQUEST_LINE_PARSED)
                        == Interceptor.BREAK){
                return true;
            }
            
            if (!disableUploadTimeout && getSelectionKey() != null) {
                ((InputReader)inputStream).setReadTimeout(uploadTimeout);
            }
          
            WorkerThread workerThread = (WorkerThread)Thread.currentThread();
            KeepAliveThreadAttachment k = 
                    (KeepAliveThreadAttachment) workerThread.getAttachment();
            k.setActiveThreadTimeout(transactionTimeout);

            inputBuffer.parseHeaders();
        
            if ( SelectorThread.isEnableNioLogging() ){                               
                logger.log(Level.INFO, 
                        "SocketChannel request line" + key.channel() + " is: " 
                        + request);
                
                logger.log(Level.INFO, "SocketChannel headers" 
                        + key.channel() + " are: "
                        + request.getMimeHeaders());
            }                       
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINEST)){
                logger.log(Level.FINEST,
                        sm.getString("processorTask.nonBlockingError"), e);
            }
            error = true;
            keepAlive = false;
            return true;
        } catch (Throwable t) {
            logger.log(Level.SEVERE,
                    sm.getString("processorTask.nonBlockingError"), t);
            // 400 - Bad Request
            response.setStatus(400);
            error = true;
        }

        // Setting up filters, and parse some request headers
        try {
            prepareRequest();
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)){
                logger.log(Level.FINE,
                        sm.getString("processorTask.createRequestError"), t);
            }
            // 400 - Internal Server Error
            response.setStatus(400);
            error = true;
        }
        return false;
    }
    
    
    /**
     * Post process the http request, after the response has been
     * commited.
     */      
    public void postProcess() throws Exception {        
        if (response.isSuspended()){
            return;
        }
        
        inputBuffer.recycle();
        outputBuffer.recycle();
        
        // Recycle ssl info
        sslSupport = null;

        if (error){
            keepAlive = false;
            connectionHeaderValueSet = false;
        }
    }
    

    /**
     * Notify the {@link TaskListener} that the request has been 
     * fully processed.
     */
    public void terminateProcess(){
        try {
            // Only one thread can invoke that method. Since we cannot
            // control how Grizzly ARP extension handle their asynchronous
            // behavior, we must make sure we are never called twice.
             if (asyncSemaphore.tryAcquire(0, TimeUnit.SECONDS)) {
                // Nobody is listening, avoid extra operation.
                if (getTaskListener() == null){
                    return;
                }
                
                TaskEvent<ProcessorTask> event = new TaskEvent<ProcessorTask>();
                if (error) {
                    event.setStatus(TaskEvent.ERROR);
                } else {
                    event.setStatus(TaskEvent.COMPLETED);
                }
                event.attach(this);
                getTaskListener().taskEvent(event);
                event.attach(null);
            } 
        } catch (InterruptedException ex) {
            if (logger.isLoggable(Level.WARNING)){
                logger.log(Level.WARNING,sm.getString("terminateProcess"),ex);
            }
        } finally {            
            asyncSemaphore.release();
        }
    }
    
    
    // -------------------------------------------------------------------- //
    
    
    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     * 
     * @param input stream from which the HTTP requests will be read
     * @param output stream which will be used to output the HTTP
     * responses
     * @return true if the connection needs to be keep-alived.
     * @throws Exception error during an I/O operation
     */
    public boolean process(InputStream input, OutputStream output)
            throws Exception {
                
        preProcess(input,output);    
        doProcess();
        postProcess();
        return keepAlive;
    }
    
    
    /** 
     * Get the request URI associated with this processor.
     */
    public String getRequestURI() {
        return request.requestURI().toString();
    }
    

    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     * 
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @SuppressWarnings("unchecked")
    public void action(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.ACTION_COMMIT) {
            // Commit current response

            if (response.isCommitted())
                return;
                        
            // Validate and write response headers
            prepareResponse();
            try {
                outputBuffer.commit();
            } catch (IOException ex) {
                logger.log(Level.FINEST,
                        sm.getString("processorTask.nonBlockingError"), ex);               
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_ACK) {

            // Acknowlege request

            // Send a 100 status back if it makes sense (response not committed
            // yet, and client specified an expectation for 100-continue)

            if ((response.isCommitted()) || (!http11))
                return;

            MessageBytes expectMB = request.getMimeHeaders().getValue("expect");
            if ((expectMB != null)
                && (expectMB.indexOfIgnoreCase("100-continue", 0) != -1)) {
                try {
                    outputBuffer.sendAck();
                } catch (IOException e) {
                    // Set error flag
                    error = true;
                }
            }

        } else if (actionCode == ActionCode.ACTION_CLOSE) {
            // Close

            // End the processing of the current request, and stop any further
            // transactions with the client

            try {
                outputBuffer.endRequest();
            } catch (IOException e) {
                logger.log(Level.FINEST,
                        sm.getString("processorTask.nonBlockingError"), e);
                // Set error flag
                error = true;
            }
        } else if (actionCode == ActionCode.ACTION_RESET) {

            // Reset response

            // Note: This must be called before the response is committed

            outputBuffer.reset();
            
        } else if (actionCode == ActionCode.ACTION_CUSTOM) {

            // Do nothing

        } else if (actionCode == ActionCode.ACTION_START) {

            started = true;

        } else if (actionCode == ActionCode.ACTION_STOP) {

            started = false;

        } else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {

            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CIPHER_SUITE_KEY, sslO);
                    sslO = sslSupport.getPeerCertificateChain(false);
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    sslO = sslSupport.getKeySize();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.KEY_SIZE_KEY, sslO);
                    sslO = sslSupport.getSessionId();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.SESSION_ID_KEY, sslO);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        sm.getString("processorTask.errorSSL") ,e);
            }

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {

            if ((remoteAddr == null) && (socket != null)) {
                InetAddress inetAddr = socket.getInetAddress();
                if (inetAddr != null) {
                    remoteAddr = inetAddr.getHostAddress();
                }   
            }
            request.remoteAddr().setString(remoteAddr);

        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {
            
            if ((localName == null) && (socket != null)) {
                InetAddress inetAddr = socket.getLocalAddress();
                if (inetAddr != null) {
                    localName = inetAddr.getHostName();
                }
            }
            request.localName().setString(localName);

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {
            
            if ((remoteHost == null) && (socket != null)) {
                InetAddress inetAddr = socket.getInetAddress();
                if (inetAddr != null) {
                    remoteHost = inetAddr.getHostName();
                }
                
                if(remoteHost == null) {
                    if(remoteAddr != null) {
                        remoteHost = remoteAddr;
                    } else { // all we can do is punt
                        request.remoteHost().recycle();
                    }
                }
            }
            request.remoteHost().setString(remoteHost);
            
        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {
                       
            if (localAddr == null)
               localAddr = socket.getLocalAddress().getHostAddress();

            request.localAddr().setString(localAddr);
            
        } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {
            
            if ((remotePort == -1 ) && (socket !=null)) {
                remotePort = socket.getPort(); 
            }    
            request.setRemotePort(remotePort);

        } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {
            
            if ((localPort == -1 ) && (socket !=null)) {
                localPort = socket.getLocalPort(); 
            }            
            request.setLocalPort(localPort);
       
        } else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {
            if( sslSupport != null) {
                /*
                 * Consume and buffer the request body, so that it does not
                 * interfere with the client's handshake messages
                 */
                InputFilter[] inputFilters = inputBuffer.getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                    .setLimit(maxPostSize);
                inputBuffer.addActiveFilter
                    (inputFilters[Constants.BUFFERED_FILTER]);
                try {
                    Object sslO = sslSupport.getPeerCertificateChain(true);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            sm.getString("processorTask.exceptionSSLcert"),e);
                }
            }
        } else if ( actionCode == ActionCode.ACTION_POST_REQUEST ) { 
            if (response.getStatus() == 200 && handler != null 
                    && compressionLevel == 0){
                try{
                    handler.handle(request,Interceptor.RESPONSE_PROCEEDED);
                } catch(IOException ex){
                    logger.log(Level.FINEST,
                            "Handler exception",ex);
                }
            }   
        } else if ( actionCode == ActionCode.CANCEL_SUSPENDED_RESPONSE ) { 
            key.attach(null);
        } else if ( actionCode == ActionCode.RESET_SUSPEND_TIMEOUT ) {
            if (key.attachment() instanceof Response.ResponseAttachment){
                Response.ResponseAttachment ra = ((Response.ResponseAttachment)key.attachment());
                if (ra != null){
                    ra.resetTimeout();
                }
            }
        } else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH ) { 
            if (key != null) {
                try{
                   outputBuffer.flush();
                } catch (IOException ex){
                    if (logger.isLoggable(Level.FINEST)){
                        logger.log(Level.FINEST,
                                "ACTION_CLIENT_FLUSH",ex); 
                    }
                    error = true;
                    response.setErrorException(ex);
                }
            } 
        }
    }

    // ------------------------------------------------------ Connector Methods


    /**
     * Set the associated adapter.
     * 
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * Get the associated adapter.
     * 
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    protected void prepareRequest() {

        http11 = true;
        http09 = false;
        contentDelimitation = false;
        if (sslSupport != null) {
            request.scheme().setString("https");
        }
        MessageBytes protocolMB = request.protocol();
        if (protocolMB.equals(Constants.HTTP_11)) {
            http11 = true;
            protocolMB.setString(Constants.HTTP_11);
        } else if (protocolMB.equals(Constants.HTTP_10)) {
            http11 = false;
            keepAlive = false;
            protocolMB.setString(Constants.HTTP_10);
        } else if (protocolMB.equals("")) {
            // HTTP/0.9
            http09 = true;
            http11 = false;
            keepAlive = false;
        } else {
            // Unsupported protocol
            http11 = false;
            error = true;
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
        }
        
        MessageBytes methodMB = request.method();
        if (methodMB.equals(Constants.GET)) {
            methodMB.setString(Constants.GET);
        } else if (methodMB.equals(Constants.POST)) {
            methodMB.setString(Constants.POST);
        }

        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue("connection");
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                keepAlive = false;
                connectionHeaderValueSet = false;
            } else if (findBytes(connectionValueBC, 
                                 Constants.KEEPALIVE_BYTES) != -1) {
                keepAlive = true;
                connectionHeaderValueSet = true;
            }
        }

        // Check user-agent header
        if ((restrictedUserAgents != null) && ((http11) || (keepAlive))) {
            MessageBytes userAgentValueMB =  
                request.getMimeHeaders().getValue("user-agent");
            if (userAgentValueMB != null) {
                // Check in the restricted list, and adjust the http11 
                // and keepAlive flags accordingly
                String userAgentValue = userAgentValueMB.toString();
                for (int i = 0; i < restrictedUserAgents.length; i++) {
                    if (restrictedUserAgents[i].equals(userAgentValue)) {
                        http11 = false;
                        keepAlive = false;
                    }
                }
            }
        }

        // Check for a full URI (including protocol://host:port/)
        ByteChunk uriBC = request.requestURI().getByteChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 0, 3, 4);
            int uriBCStart = uriBC.getStart();
            int slashPos = -1;
            if (pos != -1) {
                byte[] uriB = uriBC.getBytes();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    request.requestURI().setBytes
                        (uriB, uriBCStart + pos + 1, 1);
                } else {
                    request.requestURI().setBytes
                        (uriB, uriBCStart + slashPos, 
                         uriBC.getLength() - slashPos);
                }
                MessageBytes hostMB = headers.setValue("host");
                hostMB.setBytes(uriB, uriBCStart + pos + 3, 
                                slashPos - pos - 3);
            }

        }

        // Input filter setup
        InputFilter[] inputFilters = inputBuffer.getFilters();

        // Parse content-length header
        long contentLength = request.getContentLengthLong();
        if (contentLength >= 0) {
            
            inputBuffer.addActiveFilter
                (inputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        }

        // Parse transfer-encoding header
        MessageBytes transferEncodingValueMB = null;
        if (http11)
            transferEncodingValueMB = headers.getValue("transfer-encoding");
        if (transferEncodingValueMB != null) {
            String transferEncodingValue = transferEncodingValueMB.toString();
            // Parse the comma separated list. "identity" codings are ignored
            int startPos = 0;
            int commaPos = transferEncodingValue.indexOf(',');
            String encodingName = null;
            while (commaPos != -1) {
                encodingName = transferEncodingValue.substring
                    (startPos, commaPos).toLowerCase().trim();
                if (!addInputFilter(inputFilters, encodingName)) {
                    // Unsupported transfer encoding
                    error = true;
                    // 501 - Unimplemented
                    response.setStatus(501);
                }
                startPos = commaPos + 1;
                commaPos = transferEncodingValue.indexOf(',', startPos);
            }
            encodingName = transferEncodingValue.substring(startPos)
                .toLowerCase().trim();
            if (!addInputFilter(inputFilters, encodingName)) {
                // Unsupported transfer encoding
                error = true;
                // 501 - Unimplemented
                response.setStatus(501);
            }
        }

        MessageBytes valueMB = headers.getValue("host");

        // Check host header
        if (http11 && (valueMB == null)) {
            error = true;
            // 400 - Bad request
            response.setStatus(400);
        }

        parseHost(valueMB);

        if (!contentDelimitation) {
            // If there's no content length 
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            inputBuffer.addActiveFilter
                (inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }
    }


    /**
     * Parse host.
     */
    public void parseHost(MessageBytes valueMB) {

        if (valueMB == null || valueMB.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overriden if a host is 
            // found/parsed
            request.setServerPort(socket.getLocalPort());
            InetAddress localAddress = socket.getLocalAddress();
            // Setting the socket-related fields. The adapter doesn't know 
            // about socket.
            request.setLocalHost(localAddress.getHostName());
            request.serverName().setString(localAddress.getHostName());
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        int colonPos = -1;
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC[i] = b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            if (sslSupport == null) {
                // 80 - Default HTTTP port
                request.setServerPort(80);
            } else {
                // 443 - Default HTTPS port
                request.setServerPort(443);
            }
            request.serverName().setChars(hostNameC, 0, valueL);
        } else {

            request.serverName().setChars(hostNameC, 0, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.DEC[(int) valueB[i + valueS]];
                if (charValue == -1) {
                    // Invalid character
                    error = true;
                    // 400 - Bad request
                    response.setStatus(400);
                    break;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

    }


    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    protected void prepareResponse() {

        boolean entityBody = true;
        contentDelimitation = false;

        OutputFilter[] outputFilters = outputBuffer.getFilters();

        if (http09 == true) {
            // HTTP/0.9
            outputBuffer.addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            return;
        }

        int statusCode = response.getStatus();
        if ((statusCode == 204) || (statusCode == 205) 
            || (statusCode == 304)) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
        }

        // Check for compression
        boolean useCompression = false;
        if (entityBody && (compressionLevel > 0)) {
            useCompression = isCompressable();
            
            // Change content-length to -1 to force chunking
            if (useCompression) {
                response.setContentLength(-1);
            }
        }
        
        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        MimeHeaders headers = response.getMimeHeaders();
        if (!entityBody) {
            response.setContentLength(-1);
        } else {
            String contentType = response.getContentType();
            if (contentType != null) {
                headers.setValue("Content-Type").setString(contentType);
            } else {
                headers.setValue("Content-Type").setString(defaultResponseType);                
            }
        
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("Content-Language")
                    .setString(contentLanguage);
            }
        }

        int contentLength = response.getContentLength();
        if (contentLength != -1) {
            headers.setValue("Content-Length").setInt(contentLength);
            outputBuffer.addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            if (useChunking && entityBody && http11 && keepAlive) {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.CHUNKED_FILTER]);
                contentDelimitation = true;
                response.addHeader("Transfer-Encoding", "chunked");
            } else {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.IDENTITY_FILTER]);
            }
        }

        if (useCompression) {
            outputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
            // FIXME: Make content-encoding generation dynamic
            response.setHeader("Content-Encoding", "gzip");
            // Make Proxies happy via Vary (from mod_deflate)
            response.setHeader("Vary", "Accept-Encoding");
        }
        
        // Add date header
        if (! response.containsHeader("Date")){
            String date = FastHttpDateFormat.getCurrentDate();
            response.addHeader("Date", date);
        }
         
        // Add transfer encoding header
        // FIXME

        if ((entityBody) && (!contentDelimitation)) {
            // Mark as close the connection after the request, and add the 
            // connection: close header
            keepAlive = false;
        }

        // If we know that the request is bad this early, add the
        // Connection: close header.
        keepAlive = keepAlive && !statusDropsConnection(statusCode)
            && !dropConnection;
        if (!keepAlive ) {
            headers.setValue("Connection").setString("close");
            connectionHeaderValueSet = false;
        } else if (!http11 && !error) {
            headers.setValue("Connection").setString("Keep-Alive");
        }

        // Build the response header
        outputBuffer.sendStatus();

        int size = headers.size();
        for (int i = 0; i < size; i++) {
            outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
        }
        outputBuffer.endHeaders();

    }


    /**
     * Initialize standard input and output filters.
     */
    protected void initializeFilters() {

        // Create and add the identity filters.
        inputBuffer.addFilter(new IdentityInputFilter());
        outputBuffer.addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        inputBuffer.addFilter(new ChunkedInputFilter());
        outputBuffer.addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        inputBuffer.addFilter(new VoidInputFilter());
        outputBuffer.addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        inputBuffer.addFilter(new BufferedInputFilter());

        // Create and add the chunked filters.
        //inputBuffer.addFilter(new GzipInputFilter());
        outputBuffer.addFilter(new GzipOutputFilter());

    }


    /**
     * Add an input filter to the current request.
     * 
     * @return false if the encoding was not found (which would mean it is 
     * unsupported)
     */
    protected boolean addInputFilter(InputFilter[] inputFilters, 
                                     String encodingName) {
        if (encodingName.equals("identity")) {
            // Skip
        } else if (encodingName.equals("chunked")) {
            inputBuffer.addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = 2; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName()
                    .toString().equals(encodingName)) {
                    inputBuffer.addActiveFilter(inputFilters[i]);
                    return true;
                }
            }
            return false;
        }
        return true;
    }


    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     */
    protected int findBytes(ByteChunk bc, byte[] b) {

        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();

        // Look for first char 
        int srcEnd = b.length;

        for (int i = start; i <= (end - srcEnd); i++) {
            if (Ascii.toLower(buff[i]) != first) continue;
            // found first char, now look for a match
            int myPos = i+1;
            for (int srcPos = 1; srcPos < srcEnd; ) {
                    if (Ascii.toLower(buff[myPos++]) != b[srcPos++])
                break;
                    if (srcPos == srcEnd) return i - start; // found it
            }
        }
        return -1;

    }

    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     */
    protected boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
               status == 408 /* SC_REQUEST_TIMEOUT */ ||
               status == 411 /* SC_LENGTH_REQUIRED */ ||
               status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
               status == 414 /* SC_REQUEST_URI_TOO_LARGE */ ||
               status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
               status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
               status == 501 /* SC_NOT_IMPLEMENTED */;
    }

     /**
     * Add input or output filter.
     * 
     * @param className class name of the filter
     */
    protected void addFilter(String className) {
        try {
            Class clazz = Class.forName(className);
            Object obj = clazz.newInstance();
            if (obj instanceof InputFilter) {
                inputBuffer.addFilter((InputFilter) obj);
            } else if (obj instanceof OutputFilter) {
                outputBuffer.addFilter((OutputFilter) obj);
            } else {
                logger.log(Level.WARNING,
                        sm.getString("processorTask.unknownFilter"),className);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,sm.getString("processorTask.errorFilter"), 
                        new Object[]{className, e});
        }
    }


    /**
     * Set the maximum size of a POST which will be buffered in SSL mode.
     */
    public void setMaxPostSize(int mps) {
        maxPostSize = mps;
    }


    /**
     * Return the maximum size of a POST which will be buffered in SSL mode.
     */
    public int getMaxPostSize() {
        return maxPostSize;
    }


    /**
     * Set the socket associated with this HTTP connection.
     */
    public void setSocket(Socket socket){
        this.socket = socket;
    }

    
    /**
     * Set the upload uploadTimeout.
     */
    public void setTimeout( int timeouts ) {
        uploadTimeout = timeouts ;
    }

    /**
     * Get the upload uploadTimeout.
     */
    public int getTimeout() {
        return uploadTimeout;
    }

    /**
     * Is async HTTP write enabled.
     * @return <tt>true</tt>, if async HTTP write enabled, or <tt>false</tt>
     * otherwise.
     */
    public boolean isAsyncHttpWriteEnabled() {
        return isAsyncHttpWriteEnabled;
    }

    /**
     * Set if async HTTP write enabled.
     * @param isAsyncHttpWriteEnabled <tt>true</tt>, if async HTTP write
     * enabled, or <tt>false</tt> otherwise.
     */
    public void setAsyncHttpWriteEnabled(boolean isAsyncHttpWriteEnabled) {
        this.isAsyncHttpWriteEnabled = isAsyncHttpWriteEnabled;
    }
    
    /**
     * Register a new <code>RequestProcessor</code> instance.
     */
    private void registerMonitoring(){
        
        requestInfo = request.getRequestProcessor();
        // Add RequestInfo to RequestGroupInfo
        requestInfo.setGlobalProcessor(selectorThread.getRequestGroupInfo());

        if ( selectorThread.getManagement() == null ) return;
      
        try {
            oname = new ObjectName(selectorThread.getDomain()
                                   +  ":type=RequestProcessor,worker=http"
                                   + selectorThread.getPort()
                                   + ",name=HttpRequest" 
                                   + requestCount++ );
            selectorThread.getManagement().
                    registerComponent(requestInfo, oname,null);
        } catch( Exception ex ) {
            logger.log(Level.WARNING,
                       sm.getString("processorTask.errorRegisteringRequest"),
                       ex);
        }

        hasRequestInfoRegistered = true;
    }
    

    /**
     * Unregisters the MBean corresponding to this
     * {@link ProcessorTask}.
     */
    protected void unregisterMonitoring() {

        if ( selectorThread.getManagement() == null ) return;
        
        requestInfo = request.getRequestProcessor();
        /*
         * Remove 'requestInfo' from 'requestGroupInfo'.
         * This will also update 'requestGroupInfo' with the current stats
         * of 'requestInfo', which is why we need to reset 'requestInfo' so
         * its current stats will not be considered when it is added back to
         * 'requestGroupInfo' next time registerMonitoring() is called.
         */
        requestInfo.setGlobalProcessor(null);
        requestInfo.reset();

        if (oname != null) {
            try {
                selectorThread.getManagement().unregisterComponent(oname);
            } catch (Exception ex) {
                logger.log(Level.WARNING,
                           sm.getString("processorTask.errorUnregisteringRequest"),
                           ex);
            }
        }

        hasRequestInfoRegistered = false;
    }


    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;
    }
    
    public void setMaxHttpHeaderSize(int maxHttpHeaderSize) {
        this.maxHttpHeaderSize = maxHttpHeaderSize;
    }


    /**
     * Set the request input buffer size
     */
    public void setBufferSize(int requestBufferSize){
        this.requestBufferSize = requestBufferSize;
    }
    

    /**
     * Return the request input buffer size
     */
    public int getBufferSize(){
        return requestBufferSize;
    }
      
    /**
     * Enable or disable the keep-alive mechanism. Setting this value
     * to <tt>false</tt> will automatically add the following header to the
     * response ' Connection: close '
     */
    public void setDropConnection(boolean dropConnection){
        this.dropConnection = dropConnection;
    }
    
    
    /**
     * Is the keep-alive mechanism enabled or disabled.
     */
    public boolean getDropConnection(){
        return dropConnection;
    }

     
    /**
     * Set the {@link Interceptor} used by this class.
     */
    public void setHandler(Interceptor handler){
        this.handler = handler;
    } 
    
    
    /**
     * Return the {@link Interceptor} used by this instance.
     */
    public Interceptor getHandler(){
        return handler;
    }     
    
    
    /**
     * Set the default response type used. Specified as a semi-colon
     * delimited string consisting of content-type, encoding,
     * language, charset
     */
    public void setDefaultResponseType(String defaultResponseType){
         this.defaultResponseType = defaultResponseType;
    }


    /**
     * Return the default response type used
     */
    public String getDefaultResponseType(){
         return defaultResponseType;
    }
    
    
    /**
     * Sets the forced request type, which is forced onto requests that
     * do not already specify any MIME type.
     */
    public void setForcedRequestType(String forcedRequestType){
        this.forcedRequestType = forcedRequestType;
    }  
    
        
    /**
     * Return the default request type used
     */
    public String getForcedRequestType(){
        return forcedRequestType;
    }   
    
    
    // ------------------------------------------------------- Asynch call ---//
    
    /**
     * Enable/disable asynchronous execution of this object.
     */
    public void setEnableAsyncExecution(boolean asyncExecution){
        this.asyncExecution = asyncExecution;
    }
    
    
    /**
     * Is asynchronous execution enabled?
     */    
    public boolean isAsyncExecutionEnabled(){
        return asyncExecution;
    }
    
    
    /**
     * Set the {@link AsyncHandler} used when asynchronous execution is 
     * enabled.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler){
        this.asyncHandler = asyncHandler;     
    }
    
       
    /**
     * Return the {@link AsyncHandler} used when asynchronous execution is 
     * enabled.
     */    
    public AsyncHandler getAsyncHandler(){
        return asyncHandler;
    }


    /**
     * Return the internal <code>Request</code> object.
     */
    public Request getRequest(){
        return request;
    }

    
    /**
     * Recyle this object.
     */
    @Override
    public void recycle(){
        setTaskListener(null);
        socket = null;
        dropConnection = false;
        key = null;
    }
    
    // ----------------------------------------------------- Compression ----//
    

    /**
     * Return compression level.
     */
    public String getCompression() {
        switch (compressionLevel) {
        case 0:
            return "off";
        case 1:
            return "on";
        case 2:
            return "force";
        }
        return "off";
    }


    /**
     * Set compression level.
     */
    public void setCompression(String compression) {
        if (compression.equals("on")) {
            this.compressionLevel = 1;
        } else if (compression.equals("force")) {
            this.compressionLevel = 2;
        } else if (compression.equals("off")) {
            this.compressionLevel = 0;
        } else {
            try {
                // Try to parse compression as an int, which would give the
                // minimum compression size
                compressionMinSize = Integer.parseInt(compression);
                this.compressionLevel = 1;
            } catch (Exception e) {
                this.compressionLevel = 0;
            }
        }
    }

    
    /**
     * Add user-agent for which gzip compression didn't works
     * The user agent String given will be exactly matched
     * to the user-agent header submitted by the client.
     * 
     * @param userAgent user-agent string
     */
    public void addNoCompressionUserAgent(String userAgent) {
    	noCompressionUserAgents = addStringArray(noCompressionUserAgents, userAgent);
    }


    /**
     * Set no compression user agent list (this method is best when used with 
     * a large number of connectors, where it would be better to have all of 
     * them referenced a single array).
     */
    public void setNoCompressionUserAgents(String[] noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }


    /**
     * Return the list of no compression user agents.
     */
    public String[] findNoCompressionUserAgents() {
        return (noCompressionUserAgents);
    }


    /**
     * Add a mime-type which will be compressable
     * The mime-type String will be exactly matched
     * in the response mime-type header .
     *
     * @param mimeType mime-type string
     */
    public void addCompressableMimeType(String mimeType) {
        compressableMimeTypes =
            addStringArray(compressableMimeTypes, mimeType);
    }


    /**
     * Set compressable mime-type list (this method is best when used with
     * a large number of connectors, where it would be better to have all of
     * them referenced a single array).
     */
    public void setCompressableMimeTypes(String[] compressableMimeTypes) {
        this.compressableMimeTypes = compressableMimeTypes;
    }


    /**
     * Set compressable mime-type list
     * List contains users agents separated by ',' :
     *
     * ie: "text/html,text/xml,text/plain"
     */
    public void setCompressableMimeTypes(String compressableMimeTypes) {
        if (compressableMimeTypes != null) {
            this.compressableMimeTypes = null;
            StringTokenizer st = new StringTokenizer(compressableMimeTypes, ",");

            while (st.hasMoreTokens()) {
                addCompressableMimeType(st.nextToken().trim());
            }
        }
    }

    /**
     * Set compressable mime-type list (this method is best when used with 
     * a large number of connectors, where it would be better to have all of 
     * them referenced a single array).
     */
    public void setCompressableMimeType(String[] compressableMimeTypes) {
        this.compressableMimeTypes = compressableMimeTypes;
    }


    /**
     * Return the list of comprassable mime types.
     */
    public String[] findCompressableMimeTypes() {
        return (compressableMimeTypes);
    }
    
    
    /**
     * General use method
     * 
     * @param sArray the StringArray 
     * @param value string
     */
    private String[] addStringArray(String sArray[], String value) {
        if (value == null) {
            return sArray != null ? sArray : new String[0];
        }

        if (sArray == null) {
            sArray = new String[1];
            sArray[0] = value;
            return sArray;
        }


        String[] results = new String[sArray.length + 1];
        for (int i = 0; i < sArray.length; i++)
            results[i] = sArray[i];
        results[sArray.length] = value;
        return results;
    }

    
    /**
     * General use method
     * 
     * @param sArray the StringArray 
     * @param value string
     */
    private boolean inStringArray(String sArray[], String value) {
        for (int i = 0; i < sArray.length; i++) {
            if (sArray[i].equals(value)) {
                return true;
            }
        }
        return false;
    }
    
    
    /**
     * Checks if any entry in the string array starts with the specified value
     *
     * @param sArray the StringArray
     * @param value string
     */
    private boolean startsWithStringArray(String sArray[], String value) {
        if (value == null)
           return false;
        for (int i = 0; i < sArray.length; i++) {
            if (value.startsWith(sArray[i])) {
                return true;
            }
        }
        return false;
    }
    
    
    /**
     * Check for compression. Compression is onlky supported when http/1.1 is
     * used.
     */
    protected boolean isCompressable(){
        // Compression only since HTTP 1.1
        if (! http11)
            return false;

        // Check if browser support gzip encoding
        MessageBytes acceptEncodingMB = 
            request.getMimeHeaders().getValue("accept-encoding");
            
        if ((acceptEncodingMB == null) 
            || (acceptEncodingMB.indexOf("gzip") == -1 
            && acceptEncodingMB.indexOf("deflate") == -1))
            return false;

        // Check if content is not already gzipped
        MessageBytes contentEncodingMB =
            response.getMimeHeaders().getValue("Content-Encoding");

        if ((contentEncodingMB != null) 
            && (contentEncodingMB.indexOf("gzip") != -1 
            || contentEncodingMB.indexOf("deflate") != -1 ))
            return false;

        // If force mode, allways compress (test purposes only)
        if (compressionLevel == 2)
           return true;

        // Check for incompatible Browser
        if (noCompressionUserAgents != null) {
            MessageBytes userAgentValueMB =  
                request.getMimeHeaders().getValue("user-agent");
            if (userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();

        	if (inStringArray(noCompressionUserAgents, userAgentValue))
        		return false;
            }
        }

        // Check if suffisant len to trig the compression        
        int contentLength = response.getContentLength();
        if ((contentLength == -1) 
            || (contentLength > compressionMinSize)) {
            // Check for compatible MIME-TYPE
            if (compressableMimeTypes != null)
                return (startsWithStringArray(compressableMimeTypes, 
                        response.getContentType()));
        }

	return false;
    }
    

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }   


    /**
     * Add restricted user-agent (which will downgrade the connector 
     * to HTTP/1.0 mode). The user agent String given will be exactly matched
     * to the user-agent header submitted by the client.
     * 
     * @param userAgent user-agent string
     */
    public void addRestrictedUserAgent(String userAgent) {
    	restrictedUserAgents = addStringArray(restrictedUserAgents, userAgent);
    }


    /**
     * Set restricted user agent list (this method is best when used with 
     * a large number of connectors, where it would be better to have all of 
     * them referenced a single array).
     */
    public void setRestrictedUserAgents(String[] restrictedUserAgents) {
        this.restrictedUserAgents = restrictedUserAgents;
    }


    /**
     * Return the list of restricted user agents.
     */
    public String[] findRestrictedUserAgents() {
        return (restrictedUserAgents);
    }

    
    /**
     * Return the SSLSupport object used by this instance.
     */
    public SSLSupport getSSLSupport() {
        return sslSupport;
    }
    

    /**
     * Set the SSLSupport object used by this instance.
     */
    public void setSSLSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }

    /**
     * Return the current WorkerThread ID associated with this instance.
     */
    public long getWorkerThreadID(){
        return request.getRequestProcessor().getWorkerThreadID();
    }

    
    public boolean isKeepAlive() {
        return keepAlive;
    }

    
    public void setConnectionHeaderValueSet(boolean connectionHeaderValueSet) {
        this.connectionHeaderValueSet = connectionHeaderValueSet;
    }

    
    public boolean isError() {
        return error;
    }

    
    public InputStream getInputStream(){
        return inputStream;
    }
    
    public void setInputStream(InputStream inputStream){
        this.inputStream = inputStream;
    }
         
    
    /**
     * Set the flag to control upload time-outs.
     */
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }

    
    /**
     * Get the flag that controls upload time-outs.
     */
    public boolean getDisableUploadTimeout() {
        return disableUploadTimeout;
    }

    /** 
     * Set the maximum time, in milliseconds, a {@link WorkerThread} executing
     * an instance of this class can execute.
     * 
     * @return  the maximum time, in milliseconds
     */
    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * Set the maximum time, in milliseconds, a {@link WrokerThread} processing 
     * an instance of this class.
     * 
     * @param transactionTimeout  the maximum time, in milliseconds.
     */
    public void setTransactionTimeout(int transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    
    /**
     * Is chunking encoding used. Default is true;
     * @return Is chunking encoding used.
     */
    public boolean isUseChunking() {
        return useChunking;
    }

    
    /**
     * Enable chunking the http response. Default is true.
     * @param useChunking
     */
    public void setUseChunking(boolean useChunking) {
        this.useChunking = useChunking;
    }
}


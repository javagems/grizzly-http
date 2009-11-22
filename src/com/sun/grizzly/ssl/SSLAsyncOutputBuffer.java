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

package com.sun.grizzly.ssl;

import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLEngine;

/**
 * Buffer the bytes until the {@link ByteChunk} is full or the request
 * is completed, and then delegate the SSL encryption to class 
 * {@link SSLOutputBuffer}
 * 
 * @author Jean-Francois Arcand
 */
public class SSLAsyncOutputBuffer extends SocketChannelOutputBuffer{
    
    /**
     * Encrypted Output {@link ByteBuffer}
     */
    protected ByteBuffer outputBB;
    
    
    /**
     * The {@link SSLEngine} used to write SSL data.
     */
    protected SSLEngine sslEngine;
    
    
    /**
     * Alternate constructor.
     */
    public SSLAsyncOutputBuffer(Response response, int headerBufferSize,
                                boolean useSocketBuffer) {
        super(response,headerBufferSize,useSocketBuffer);     
    }    
        
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * using {@link SSLOutputBuffer}
     * @param bb the ByteBuffer to write.
     */   
    @Override
    public void flushChannel(ByteBuffer bb) throws IOException{
        SSLOutputWriter.flushChannel((SocketChannel)channel, bb, outputBB, sslEngine);
    }   
    
    /**
     * Return the encrypted {@link ByteBuffer} used to handle response.
     */    
    public ByteBuffer getOutputBB(){
        return outputBB;
    }
    
    
    /**
     * Set the encrypted {@link ByteBuffer} used to handle response.
     */   
    public void setOutputBB(ByteBuffer outputBB){
        this.outputBB = outputBB;
    }
    
         
    /**
     * Set the{@link SSLEngine}.
     */
    public SSLEngine getSSLEngine() {
        return sslEngine;
    }

        
    /**
     * Get the{@link SSLEngine}.
     */
    public void setSSLEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }    
}

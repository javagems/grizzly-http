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

import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.SSLUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;

/**
 * This class add support for TLS|SSL to a {@link InputReader}.
 *
 * @author Jean-Francois Arcand
 */
public class SSLAsyncStream extends InputReader {

    /**
     * The SSLEngine to use for unwrapping bytes.
     */
    private SSLEngine sslEngine;
    
    
    /**
     * The encrypted {@link ByteBuffer}
     */
    private ByteBuffer inputBB;
    
    
    public SSLAsyncStream (final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }
    
    /**
     * Read and decrypt bytes from the underlying SSL connections. All
     * the SSLEngine operations are delegated to class {@link SSLUtils}.
     */
    @Override
    protected int doRead(){   
        int initialPosition = byteBuffer.position();
        int byteRead = 0;
        while (byteBuffer.position() == initialPosition){
            int count = SSLUtils.doRead((SocketChannel) key.channel(), inputBB,
                    sslEngine, readTimeout);
            if (count > 0) {
                byteRead += count;
                try{
                    byteBuffer = SSLUtils.unwrapAll(byteBuffer,inputBB,sslEngine);
                } catch (IOException ex){
                    Logger logger = SSLSelectorThread.logger();
                    if ( logger.isLoggable(Level.FINE) )
                        logger.log(Level.FINE,"SSLUtils.unwrapAll",ex);
                    return -1;
                }
            } else if (count == -1 && byteRead == 0) {
                byteRead = -1;
                break;
            } else {
                break;
            }   
        }

        byteBuffer.flip();
        return byteRead;
    } 

    
    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    
    public void setSslEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }

    
    public ByteBuffer getInputBB() {
        return inputBB;
    }

    
    public void setInputBB(ByteBuffer inputBB) {
        this.inputBB = inputBB;
    }
}


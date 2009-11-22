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

package com.sun.grizzly.http;


/**
 * Sample event object used by instance of {@link TaskListener} to share
 * status information about where they are when processing a request.
 *
 * @author Jean-Francois Arcand
 */
public class TaskEvent<E> {
 
    public final static int START = 0;
    public final static int ERROR = 1; 
    public final static int COMPLETED = 2;
    public final static int CONTINUE = 3;
    public final static int START_ASYNCHRONOUS = 4;
    
    private int status;
    
 
    public int getStatus(){
        return status;
    }
    
    
    public void setStatus(int status){
        this.status = status;
    }

    
    /**
     * The associated {@link TaskContext} instance. Can be null.
     */
    protected E ctx;

    
    /**
     * Create an instance and associated the {@link TaskContext} object.
     */
    public TaskEvent(E ctx){
        this.ctx = ctx;
    }
    
    
    /**
     * Create an empty instance.
     */
    public TaskEvent(){
    }
  
    
    /**
     * Set {@link TaskContext} instance. Can be null.
     */
    public void attach(E ctx){
        this.ctx = ctx;
    }

    
    /**
     * Return the {@link TaskContext} instance. Can be null.
     */
    public E attachement(){
        return ctx;
    }  
    
    
    
}



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

package com.sun.grizzly.arp;



/**
 * An interface marker used to execute operations before 
 * a {@link AsyncProcesssorTask} in pre/post or interrupted. Usualy, 
 * implementation of this interface is called by an instance of 
 * {@link AsyncExecutor}.
 * 
 * Using an {@link AsyncExecutor}, it is possible to suspend or resume 
 * the current request processing. Once suspended, the request can always 
 * be resumed using the {@link AsyncExecutor}, and the normal Grizzly
 * invokation path will be used (like calling {@link Adapter#service} 
 * or {@link GrizzlyAdapter#service}). 
 * 
 * To invoke the {@link GrizzlyAdapter} from an AsyncFilter, just invoke
 * {@link AsyncExecutor#execute}, and then calling {@link AsyncExecutor#postExecute}
 * will commit the response.
 *
 * Implementation of this interface must be thread-safe.
 *
 * @author Jeanfrancois Arcand
 */
public interface AsyncFilter {
    
    /**
     * Execute and return <tt>true</tt> if the next {@link AsyncFilter} 
     * can be invoked. Return <tt>false</tt> to stop calling the 
     * {@link AsyncFilter}.
     */
    public boolean doFilter(AsyncExecutor asyncExecutor);
}

/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     thibaud
 */
package org.nuxeo.s3utils;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * 
 * @since 8.2
 */
public class S3HandlerServiceImpl extends DefaultComponent implements S3HandlerService {

    protected static final String XP = "configuration";
    
    protected HashMap<String, S3HandlerDescriptor> contributions = new HashMap<String, S3HandlerDescriptor>();
    protected HashMap<String, S3Handler> s3Handlers = new HashMap<String, S3Handler>();
    
    // ==========================================================
    // ==================== DefaultComponent ====================
    // ==========================================================
    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }
    
    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.activate(context);
        contributions.clear();
    }
    
    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP.equals(extensionPoint)) {
            if (contribution instanceof S3HandlerDescriptor) {
                registerS3Handler((S3HandlerDescriptor) contribution);
            } else {
                throw new NuxeoException("Invalid descriptor: " + contribution.getClass());
            }
        } else {
            throw new NuxeoException("Invalid extension point: " + extensionPoint);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP.equals(extensionPoint)) {
            if (contribution instanceof S3HandlerDescriptor) {
                unregisterS3Handler((S3HandlerDescriptor) contribution);
            }
        }
    }
    
    protected void registerS3Handler(S3HandlerDescriptor desc) {
        contributions.put(desc.getName(), desc);
        // lookup now to have immediate feedback on error
        getS3Handler(desc.getName());
    }
    
    protected void unregisterS3Handler(S3HandlerDescriptor desc) {
        contributions.remove(desc.getName());
        S3Handler handler = s3Handlers.get(desc.getName());
        if(handler != null) {
            handler.cleanup();
            s3Handlers.remove(desc.getName());
        }
    }
    
    // ==========================================================
    // ==================== S3HandlerService ====================
    // ==========================================================
    /**
     * Returns the S3Handler given it's name. Returns <code>null</code> if not found.
     * 
     * @param name
     * @return the contributed S3Handler
     * @since 8.2
     */
    @Override
    public synchronized S3Handler getS3Handler(String name) {
        
        S3Handler handler = (S3Handler) s3Handlers.get(name);
        
        if(handler == null) {
            S3HandlerDescriptor desc = contributions.get(name);
            if (desc == null) {
                return null;
            }
            Class<?> klass = desc.klass;
            try {
                if (S3Handler.class.isAssignableFrom(klass)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends S3Handler> s3HandlerClass = (Class<? extends S3Handler>) klass;
                    handler = s3HandlerClass.newInstance();
                } else {
                    throw new RuntimeException("Unknown class for S3Handler: " + klass);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            try {
                handler.initialize(desc);
            } catch(NuxeoException e) {
                throw new RuntimeException(e);
            }
            
            s3Handlers.put(name, handler);
        }
        
        
        return handler;
    }

}

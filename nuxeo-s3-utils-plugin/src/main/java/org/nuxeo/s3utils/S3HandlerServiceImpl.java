/*
 * (C) Copyright 2023 Hyland (http://hyland.com/) and others.
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
 *     Thibaud Arguillere
 */
package org.nuxeo.s3utils;

import java.util.HashMap;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Implementation of service: Loads contributions, initializes handlers, ...
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
        super.deactivate(context);
        /*
         * Every handler holds an S3Client, a CRT S3AsyncClient and an S3TransferManager. Only S3Handler#cleanup closes
         * them, so not calling it here leaks the AWS clients, their thread pools and the CRT native resources on every
         * shutdown and on every hot reload.
         */
        cleanupAllHandlers();
        contributions.clear();
    }

    /**
     * Releases every handler built so far, and forgets them.
     *
     * @since 2025.1
     */
    protected synchronized void cleanupAllHandlers() {
        s3Handlers.values().forEach(S3Handler::cleanup);
        s3Handlers.clear();
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

    protected synchronized void registerS3Handler(S3HandlerDescriptor desc) {
        String name = desc.getName();
        contributions.put(name, desc);
        /*
         * A contribution may override an already registered one, which is how a Studio project customizes the
         * "default" handler. getS3Handler only builds a handler when it has none cached, so the previously built one
         * must be released here, otherwise the override is silently ignored and the old bucket and region keep being
         * used.
         */
        S3Handler previous = s3Handlers.remove(name);
        if (previous != null) {
            previous.cleanup();
        }
        // lookup now to have immediate feedback on error
        getS3Handler(name);
    }

    protected synchronized void unregisterS3Handler(S3HandlerDescriptor desc) {
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

        S3Handler handler = s3Handlers.get(name);

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
                    handler = s3HandlerClass.getDeclaredConstructor().newInstance();
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

/*
 * NewConnectionDialog.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.connections.ui;


import org.rstudio.core.client.Debug;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.RStudioFrame;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.application.model.ApplicationServerOperations;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.HelpLink;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.server.remote.RResult;
import org.rstudio.studio.client.shiny.events.ShinyFrameNavigatedEvent;
import org.rstudio.studio.client.workbench.prefs.model.UIPrefs;
import org.rstudio.studio.client.workbench.views.connections.events.NewConnectionDialogUpdatedEvent;
import org.rstudio.studio.client.workbench.views.connections.model.ConnectionOptions;
import org.rstudio.studio.client.workbench.views.connections.model.ConnectionsServerOperations;
import org.rstudio.studio.client.workbench.views.connections.model.NewConnectionContext;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class NewConnectionDialog extends ModalDialog<ConnectionOptions>
                                 implements ShinyFrameNavigatedEvent.Handler,
                                            NewConnectionDialogUpdatedEvent.Handler
{
   @Inject
   private void initialize(UIPrefs uiPrefs,
                           EventBus events,
                           GlobalDisplay globalDisplay,
                           ConnectionsServerOperations server)
   {
      uiPrefs_ = uiPrefs;
      events_ = events;
      globalDisplay_ = globalDisplay;
      server_ = server;

      events.addHandler(ShinyFrameNavigatedEvent.TYPE, this);
      events.addHandler(NewConnectionDialogUpdatedEvent.TYPE, this);
   }
   
   public NewConnectionDialog(NewConnectionContext context,
                              OperationWithInput<ConnectionOptions> operation,
                              Operation cancelOperation)
   {
      super("New Connection", operation, cancelOperation);
      RStudioGinjector.INSTANCE.injectMembers(this);
      
      context_ = context;
      
      setOkButtonCaption("Connect");
           
      HelpLink helpLink = new HelpLink(
            "Using Spark with RStudio",
            "using_spark",
            false);
      helpLink.addStyleName(RES.styles().helpLink());
      addLeftWidget(helpLink);   
   }

   private void showError(String errorMessage)
   {
      globalDisplay_.showErrorMessage("Error", errorMessage);
   }
   
   @Override
   protected void onDialogShown()
   {
      super.onDialogShown();
      frame_.getWindow().focus();

      // initialize miniUI
      server_.launchEmbeddedShinyConnectionUI("sparklyr", new ServerRequestCallback<RResult<Void>>()
      {
         @Override
         public void onResponseReceived(RResult<Void> response)
         {
            if (response.failed()) {
               showError(response.errorMessage());
               closeDialog();
            }
         }

         @Override
         public void onError(ServerError error)
         {
            Debug.logError(error);
            showError(error.getUserMessage());
            closeDialog();
         }
      });
   }
   
   @Override
   protected boolean validate(ConnectionOptions result)
   {
      return true;
   }
   
   @Override
   protected Widget createMainWidget()
   {
      VerticalPanel container = new VerticalPanel();    
      
      // create iframe for miniUI
      frame_ = new RStudioFrame();
      frame_.setSize("100%", "125px");

      container.add(frame_);      
      
      // add the code panel     
      codePanel_ = new ConnectionCodePanel();
      codePanel_.addStyleName(RES.styles().dialogCodePanel());
      final Command updateOKButtonCommand = new Command() {
         @Override
         public void execute()
         {
            if (codePanel_.getConnectVia().equals(ConnectionOptions.CONNECT_COPY_TO_CLIPBOARD))
               setOkButtonCaption("Copy");
            else
               setOkButtonCaption("Connect");
         }
      };

      updateOKButtonCommand.execute();
      codePanel_.addConnectViaChangeHandler(new ChangeHandler() {
         @Override
         public void onChange(ChangeEvent event)
         {
            updateOKButtonCommand.execute();
         }
      });
      
      final Command updateCodeCommand = new Command() {
         @Override
         public void execute()
         {
            codePanel_.setCode("", null);
         }
      };
      updateCodeCommand.execute();

      Grid codeGrid = new Grid(1, 1);
      codeGrid.addStyleName(RES.styles().codeGrid());
      codeGrid.setCellPadding(0);
      codeGrid.setCellSpacing(0);
      codeGrid.setWidget(0, 0, codePanel_);
      container.add(codeGrid);
     
      return container;
   }

   @Override
   protected ConnectionOptions collectInput()
   {
      // collect the result
      ConnectionOptions result = ConnectionOptions.create(
         codePanel_.getCode(),
         codePanel_.getConnectVia());
      
      // return result
      return result;
   }

   @Override
   public void onShinyFrameNavigated(ShinyFrameNavigatedEvent event)
   {
      String url = event.getURL();
      
      if (Desktop.isDesktop())
         Desktop.getFrame().setShinyDialogUrl(url);

      frame_.setUrl(StringUtil.makeAbsoluteUrl(url));
   }
   
   @Override
   public void onNewConnectionDialogUpdated(NewConnectionDialogUpdatedEvent event)
   {
      codePanel_.setCode(event.getCode(), "");
   }
   
   public interface Styles extends CssResource
   {
      String helpLink();
      String codeViewer();
      String codeGrid();
      String codePanelHeader();
      String dialogCodePanel();
      String infoPanel();
      String leftLabel();
   }

   public interface Resources extends ClientBundle
   {
      @Source("NewConnectionDialog.css")
      Styles styles();
   }
   
   public static Resources RES = GWT.create(Resources.class);
   public static void ensureStylesInjected() 
   {
      RES.styles().ensureInjected();
   }
   
   private final NewConnectionContext context_;
   
   private ConnectionCodePanel codePanel_;
     
   private UIPrefs uiPrefs_;
   private EventBus events_;
   private RStudioFrame frame_;
   private GlobalDisplay globalDisplay_;
   private ConnectionsServerOperations server_;
}

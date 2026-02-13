/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { Location } from "@angular/common";
import { AfterViewInit, Component, HostListener, OnDestroy, OnInit, ViewChild, ViewContainerRef } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { UserService } from "../../common/service/user/user.service";
import { WorkflowPersistService } from "../../common/service/workflow-persist/workflow-persist.service";
import { Workflow } from "../../common/type/workflow";
import { OperatorMetadataService } from "../service/operator-metadata/operator-metadata.service";
import { UndoRedoService } from "../service/undo-redo/undo-redo.service";
import { WorkflowCacheService } from "../service/workflow-cache/workflow-cache.service";
import { WorkflowActionService } from "../service/workflow-graph/model/workflow-action.service";
import { WorkflowUtilService } from "../service/workflow-graph/util/workflow-util.service";
import { NzMessageService } from "ng-zorro-antd/message";
import { debounceTime, distinctUntilChanged, filter, switchMap, throttleTime } from "rxjs/operators";
import { UntilDestroy, untilDestroyed } from "@ngneat/until-destroy";
import { of } from "rxjs";
import { isDefined } from "../../common/util/predicate";
import { NotificationService } from "src/app/common/service/notification/notification.service";
import { WorkflowConsoleService } from "../service/workflow-console/workflow-console.service";
import { OperatorReuseCacheStatusService } from "../service/workflow-status/operator-reuse-cache-status.service";
import { CodeEditorService } from "../service/code-editor/code-editor.service";
import { WorkflowMetadata } from "src/app/dashboard/type/workflow-metadata.interface";
import { EntityType, HubService } from "../../hub/service/hub.service";
import { THROTTLE_TIME_MS } from "../../hub/component/workflow/detail/hub-workflow-detail.component";
import { WorkflowCompilingService } from "../service/compile-workflow/workflow-compiling.service";
import { DASHBOARD_USER_WORKSPACE } from "../../app-routing.constant";
import { GuiConfigService } from "../../common/service/gui-config.service";

export const SAVE_DEBOUNCE_TIME_IN_MS = 5000;

@UntilDestroy()
@Component({
  selector: "texera-workspace",
  templateUrl: "./workspace.component.html",
  styleUrls: ["./workspace.component.scss"],
  providers: [
    // uncomment this line for manual testing without opening backend server
    // { provide: OperatorMetadataService, useClass: StubOperatorMetadataService },
  ],
})
export class WorkspaceComponent implements AfterViewInit, OnInit, OnDestroy {
  public pid?: number = undefined;
  public writeAccess: boolean = false;
  public isLoading: boolean = false;
  @ViewChild("codeEditor", { read: ViewContainerRef }) codeEditorViewRef!: ViewContainerRef;

  private autoPersistRegistered = false;

  constructor(
    private userService: UserService,
    // additional services for lifecycle
    private workflowCompilingService: WorkflowCompilingService,
    private workflowConsoleService: WorkflowConsoleService,
    private operatorReuseCacheStatusService: OperatorReuseCacheStatusService,
    private undoRedoService: UndoRedoService,
    private workflowCacheService: WorkflowCacheService,
    private workflowPersistService: WorkflowPersistService,
    private workflowActionService: WorkflowActionService,
    private workflowUtilService: WorkflowUtilService,  // Injected here
    private location: Location,
    private route: ActivatedRoute,
    private operatorMetadataService: OperatorMetadataService,
    private message: NzMessageService,
    private router: Router,
    private notificationService: NotificationService,
    private hubService: HubService,
    private codeEditorService: CodeEditorService,
    private config: GuiConfigService
  ) {}

  ngOnInit() {
    this.pid = parseInt(this.route.snapshot.queryParams.pid) || undefined;
    this.workflowActionService.setHighlightingEnabled(true);

    const navState = window.history.state;
    if (navState && navState.workflow) {
      try {
        // Cast to any to avoid TS errors on .clear()
        (this.workflowActionService.getJointGraph() as any).clear();
        (this.workflowActionService.getTexeraGraph() as any).clear();
        this.workflowActionService.reloadWorkflow(navState.workflow);
        console.log("Workflow loaded from navigation state!");
      } catch (error) {
        console.error("Failed to load workflow from navigation state:", error);
      }
    }
  }

  ngAfterViewInit(): void {
    this.workflowActionService.resetAsNewWorkflow();

    if (this.config.env.userSystemEnabled) {
      const widInRoute = this.route.snapshot.params.id;
      if (widInRoute) {
        this.isLoading = true;
        this.workflowActionService.disableWorkflowModification();
      }
      this.onWIDChange();
      this.updateViewCount();
    }

    this.registerLoadOperatorMetadata();
    this.codeEditorService.vc = this.codeEditorViewRef;
  }

  @HostListener("window:beforeunload")
  ngOnDestroy() {
    if (this.userService.isLogin() && this.workflowPersistService.isWorkflowPersistEnabled()) {
      const workflow = this.workflowActionService.getWorkflow();
      this.workflowPersistService.persistWorkflow(workflow).pipe(untilDestroyed(this)).subscribe();
    }

    this.codeEditorViewRef.clear();

    // If your WorkflowActionService does NOT have clearWorkflow(),
    // replace with resetAsNewWorkflow(), else keep as is.
    if (typeof this.workflowActionService.clearWorkflow === "function") {
      this.workflowActionService.clearWorkflow();
    } else {
      this.workflowActionService.resetAsNewWorkflow();
    }
  }

  registerAutoCacheWorkFlow(): void {
    this.workflowActionService
      .workflowChanged()
      .pipe(debounceTime(SAVE_DEBOUNCE_TIME_IN_MS))
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        this.workflowCacheService.setCacheWorkflow(this.workflowActionService.getWorkflow());
      });
  }

  registerAutoPersistWorkflow(): void {
    if (this.autoPersistRegistered) {
      return;
    }
    this.autoPersistRegistered = true;

    this.workflowActionService
      .workflowChanged()
      .pipe(debounceTime(SAVE_DEBOUNCE_TIME_IN_MS))
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        if (this.userService.isLogin() && this.workflowPersistService.isWorkflowPersistEnabled()) {
          this.workflowPersistService
            .persistWorkflow(this.workflowActionService.getWorkflow())
            .pipe(untilDestroyed(this))
            .subscribe((updatedWorkflow: Workflow) => {
              if (this.workflowActionService.getWorkflowMetadata().wid !== updatedWorkflow.wid) {
                this.location.go(`${DASHBOARD_USER_WORKSPACE}/${updatedWorkflow.wid}`);
              }
              this.workflowActionService.setWorkflowMetadata(updatedWorkflow);
            });
        }
      });
  }

  loadWorkflowWithId(wid: number): void {
    this.isLoading = true;
    this.workflowActionService.disableWorkflowModification();
    this.workflowPersistService
      .retrieveWorkflow(wid)
      .pipe(untilDestroyed(this))
      .subscribe(
        (workflow: Workflow) => {
          this.workflowActionService.setNewSharedModel(wid, this.userService.getCurrentUser());
          const fragment = this.route.snapshot.fragment;
          this.workflowActionService.reloadWorkflow(workflow);
          this.workflowActionService.enableWorkflowModification();
          this.router.navigate([], {
            relativeTo: this.route,
            fragment: fragment !== null ? fragment : undefined,
            preserveFragment: false,
          });
          if (fragment) {
            if (this.workflowActionService.getTexeraGraph().hasElementWithID(fragment)) {
              this.workflowActionService.highlightElements(false, fragment);
            } else {
              this.notificationService.error(`Element ${fragment} doesn't exist`);
              this.router.navigate([], { relativeTo: this.route });
            }
          }
          this.undoRedoService.clearUndoStack();
          this.undoRedoService.clearRedoStack();
          this.isLoading = false;
          this.registerAutoPersistWorkflow();
          this.triggerCenter();
        },
        () => {
          this.workflowActionService.resetAsNewWorkflow();
          this.workflowActionService.enableWorkflowModification();
          this.undoRedoService.clearUndoStack();
          this.undoRedoService.clearRedoStack();
          this.message.error("You don't have access to this workflow, please log in with an appropriate account");
          this.isLoading = false;
        }
      );
  }

  registerLoadOperatorMetadata() {
    this.operatorMetadataService
      .getOperatorMetadata()
      .pipe(untilDestroyed(this))
      .subscribe(() => {
        let wid = this.route.snapshot.params.id;
        if (this.config.env.userSystemEnabled) {
          if (wid) {
            this.isLoading = true;
            this.workflowActionService.disableWorkflowModification();
            this.userService
              .userChanged()
              .pipe(untilDestroyed(this))
              .subscribe(() => {
                this.loadWorkflowWithId(wid);
              });
          } else {
            this.registerAutoPersistWorkflow();
          }
        } else {
          const fragment = this.route.snapshot.fragment;
          const cachedWorkflow = this.workflowCacheService.getCachedWorkflow();
          this.registerAutoCacheWorkFlow();
          this.workflowActionService.reloadWorkflow(cachedWorkflow);
          this.router.navigate([], {
            relativeTo: this.route,
            fragment: fragment !== null ? fragment : undefined,
            preserveFragment: false,
          });
          if (fragment) {
            if (this.workflowActionService.getTexeraGraph().hasElementWithID(fragment)) {
              this.workflowActionService.highlightElements(false, fragment);
            } else {
              this.notificationService.error(`Element ${fragment} doesn't exist`);
              this.router.navigate([], { relativeTo: this.route });
            }
          }
          this.undoRedoService.clearUndoStack();
          this.undoRedoService.clearRedoStack();
        }
      });
  }

  onWIDChange() {
    this.workflowActionService
      .workflowMetaDataChanged()
      .pipe(
        switchMap(() => of(this.workflowActionService.getWorkflowMetadata())),
        filter((metadata: WorkflowMetadata) => isDefined(metadata.wid)),
        distinctUntilChanged()
      )
      .pipe(untilDestroyed(this))
      .subscribe((metadata: WorkflowMetadata) => {
        this.writeAccess = !metadata.readonly;
      });
  }

  updateViewCount() {
    let wid = this.route.snapshot.params.id;
    let uid = this.userService.getCurrentUser()?.uid;
    this.hubService
      .postView(wid, uid ? uid : 0, EntityType.Workflow)
      .pipe(throttleTime(THROTTLE_TIME_MS))
      .pipe(untilDestroyed(this))
      .subscribe();
  }

  public triggerCenter(): void {
    this.workflowActionService.getTexeraGraph().triggerCenterEvent();
  }
}

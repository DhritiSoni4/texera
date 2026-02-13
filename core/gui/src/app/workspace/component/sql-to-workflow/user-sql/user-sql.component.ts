import { Component, OnInit } from '@angular/core';
import { DatasetService } from "../../../../dashboard/service/user/dataset/dataset.service";
import { DashboardDataset } from "../../../../dashboard/type/dashboard-dataset.interface";
import { DatasetVersion } from '../../../../common/type/dataset';
import { DatasetFileNode } from '../../../../common/type/datasetVersionFileTree';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { WorkflowActionService } from 'src/app/workspace/service/workflow-graph/model/workflow-action.service';
import { UndoRedoService } from '../../../../workspace/service/undo-redo/undo-redo.service';
import { NzModalRef, NzModalService } from 'ng-zorro-antd/modal';
import { firstValueFrom } from 'rxjs';
import { FileSelectionComponent } from 'src/app/workspace/component/file-selection/file-selection.component';
import { parseFilePathToDatasetFile } from '../../../../common/type/dataset-file';

interface TableBindingRow {
  name: string;
  datasetId: number | null;
  versions: DatasetVersion[];
  versionId: number | null;
  files: DatasetFileNode[];
  path: string | null;
  hasHeader: boolean;
  inferredTypes?: Record<string, string>;
  columnNames?: string[];
}

interface BindingPayloadItem {
  path: string;
  hasHeader: boolean;
  columnTypes: Record<string, string>;
  inferredTypes: Record<string, string>;
  customDelimiter: string;
  fileEncoding?: string;
  columnNames?: string[];
}

interface BindingPayload {
  [key: string]: BindingPayloadItem;
}

@Component({
  selector: 'app-user-sql',
  templateUrl: './user-sql.component.html',
  styleUrls: ['./user-sql.component.scss'],
})
export class UserSqlComponent implements OnInit {
  previewData: Array<Record<string, any>> = [];
  columnTypes: Record<string, string> = {};
  persistedColumnTypes: Record<string, Record<string, string>> = {};
  previewForPath: string | null = null;
  showPreviewModal = false;

  datasets: DashboardDataset[] = [];
  tableBindings: TableBindingRow[] = [];
  sqlQuery: string = '';

  readonly backendSupportsMultipleTables = true;
  isSubmitting = false;
  lastResponse: any = null;
  workflowLoaded = false;

  constructor(
    private http: HttpClient,
    private datasetService: DatasetService,
    private workflowActionService: WorkflowActionService,
    private undoRedoService: UndoRedoService,
    private modalRef: NzModalRef,
    private modal: NzModalService
  ) {}

  ngOnInit(): void {
    this.datasetService.retrieveAccessibleDatasets().subscribe({
      next: (data: DashboardDataset[]) => {
        this.datasets = data;
        this.addBinding();
      },
      error: (err) => console.error('Failed to load datasets:', err),
    });
  }

  // ---------------- CSV Preview ----------------
  onPreviewCsvClick(index: number): void {
    const row = this.tableBindings[index];
    if (row.path) this.onDatasetSelected(row.path, row.hasHeader);
    else alert('Cannot preview CSV: no valid file path selected.');
  }

  onDatasetSelected(filePath: string, hasHeader: boolean): void {
    this.datasetService.retrieveDatasetVersionSingleFile(filePath).subscribe({
      next: (blob) => {
        const reader = new FileReader();
        reader.onload = () => {
          const csvText = reader.result as string;

          const lines = csvText.split(/\r?\n/).filter(l => l.trim() !== '');
          if (!lines.length) return;

          let headers: string[] = [];
          const dataLines = [...lines];

          if (hasHeader) {
            headers = dataLines.shift()!.split(',');
          } else {
            const firstRowCols = dataLines[0]?.split(',') || [];
            headers = firstRowCols.map((_, idx) => 'column-' + (idx + 1));
          }

          const rows = dataLines.slice(0, 10).map(line => line.split(','));
          const previewData: Array<Record<string, any>> = rows.map(row =>
            headers.reduce((acc, h, idx) => ({ ...acc, [h]: row[idx] ?? '' }), {})
          );

          this.previewData = previewData;

          const row = this.tableBindings.find(r => r.path === filePath);
          if (row) {
            row.inferredTypes = this.inferCsvScanColumnTypes(previewData);
            row.columnNames = headers.map(h => h.toUpperCase()); // <-- always set columnNames
          }

          this.columnTypes = { ...(this.persistedColumnTypes[filePath] || (row?.inferredTypes || {})) };
          this.previewForPath = filePath;
          this.showPreviewModal = true;
        };
        reader.readAsText(blob);
      },
      error: () => alert('Error fetching CSV file preview.'),
    });
  }


  inferCsvScanColumnTypes(data: any[]): Record<string, string> {
    if (!data.length) return {};

    const cols = Object.keys(data[0]);
    const inferred: Record<string, string> = {};

    cols.forEach(col => {
      const types = data.map(row => this.getCsvScanType(row[col]));
      if (types.every(t => t === 'integer' || t === 'null')) inferred[col] = 'integer';
      else if (types.every(t => t === 'integer' || t === 'double' || t === 'null')) inferred[col] = 'double';
      else if (types.every(t => t === 'boolean' || t === 'null')) inferred[col] = 'boolean';
      else if (types.every(t => t === 'timestamp' || t === 'null')) inferred[col] = 'timestamp';
      else inferred[col] = 'string';
    });

    return inferred;
  }

  getCsvScanType(value: string): string {
    if (value === null || value === undefined || value.trim() === '') return 'null';
    const lower = value.toLowerCase();
    if (lower === 'true' || lower === 'false') return 'boolean';
    if (!isNaN(Number(value))) return Number(value) % 1 === 0 ? 'integer' : 'double';
    if (!isNaN(Date.parse(value))) return 'timestamp';
    return 'string';
  }

  onColumnTypeChange(col: string, value: string) {
    if (!this.previewForPath) return;

    if (!this.persistedColumnTypes[this.previewForPath]) {
      this.persistedColumnTypes[this.previewForPath] = {};
    }
    this.persistedColumnTypes[this.previewForPath][col] = value;
  }

  getColumnTypeForPreview(colKey: string, path?: string): string {
    const effectivePath = path || this.previewForPath;
    if (!effectivePath) return this.columnTypes[colKey] || 'string';
    return this.persistedColumnTypes[effectivePath]?.[colKey] || this.columnTypes[colKey] || 'string';
  }

  closePreviewModal(): void {
    this.showPreviewModal = false;
    this.previewData = [];
    this.columnTypes = {};
    this.previewForPath = null;
  }

  // ---------------- Table Bindings ----------------
  addBinding(): void {
    this.tableBindings.push({
      name: '',
      datasetId: null,
      versions: [],
      versionId: null,
      files: [],
      path: null,
      hasHeader: true,
    });
  }

  removeBinding(i: number): void {
    this.tableBindings.splice(i, 1);
  }

  onRowDatasetChange(i: number, selectedDatasetId: number): void {
    const row = this.tableBindings[i];
    row.datasetId = selectedDatasetId || null;
    row.versionId = null;
    row.files = [];
    row.path = null;
    if (row.datasetId !== null) {
      this.datasetService.retrieveDatasetVersionList(row.datasetId).subscribe({
        next: (versions) => (row.versions = versions),
        error: (err) => console.error('Failed to load versions:', err),
      });
    }
  }

  onRowVersionChange(i: number, selectedVersionId: number): void {
    const row = this.tableBindings[i];
    row.versionId = selectedVersionId || null;
    row.files = [];
    row.path = null;
    if (row.datasetId !== null && row.versionId !== null) {
      this.datasetService.retrieveDatasetVersionFileTree(row.datasetId, row.versionId).subscribe({
        next: (data) => (row.files = data.fileNodes ? this.getCsvFiles(data.fileNodes) : []),
        error: (err) => {
          console.error('Failed to load file tree:', err);
          row.files = [];
        },
      });
    }
  }

  private getCsvFiles(nodes: DatasetFileNode[]): DatasetFileNode[] {
    let out: DatasetFileNode[] = [];
    for (const n of nodes) {
      if (n.type === 'file' && n.name.endsWith('.csv')) out.push(n);
      else if (n.type === 'directory' && n.children) out = out.concat(this.getCsvFiles(n.children));
    }
    return out;
  }

  onRowHasHeaderToggle(i: number, e: Event) {
    this.tableBindings[i].hasHeader = !!(e.target as HTMLInputElement).checked;
  }

  isValidIdentifier(name: string | null | undefined): boolean {
    return !!name && /^[A-Za-z_][A-Za-z0-9_]*$/.test(name);
  }

  private hasDuplicateNames(): boolean {
    const seen = new Set<string>();
    for (const r of this.tableBindings) {
      if (!r.name) return true;
      const key = r.name.toLowerCase();
      if (seen.has(key)) return true;
      seen.add(key);
    }
    return false;
  }

  hasInvalidRows(): boolean {
    if (!this.tableBindings.length) return true;
    if (this.hasDuplicateNames()) return true;
    for (const r of this.tableBindings) {
      if (!this.isValidIdentifier(r.name) || r.datasetId === null || r.versionId === null || !r.path) return true;
    }
    return false;
  }

  isRunDisabled(): boolean {
    if (!this.sqlQuery?.trim()) return true;
    if (this.hasInvalidRows()) return true;
    if (!this.backendSupportsMultipleTables && this.tableBindings.length > 1) return true;
    return false;
  }

  // ---------------- New: infer columns directly from CSV if not previewed ----------------
  private async inferColumnNamesFromCsv(path: string, hasHeader: boolean): Promise<string[]> {
    const blob = await firstValueFrom(this.datasetService.retrieveDatasetVersionSingleFile(path));
    const text = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(blob);
    });

    const lines = text.split(/\r?\n/).filter(l => l.trim() !== '');
    if (!lines.length) return [];

    let headers: string[] = [];
    const dataLines = [...lines];

    if (hasHeader) {
      headers = dataLines.shift()!.split(',');
    } else {
      const firstRowCols = dataLines[0]?.split(',') || [];
      headers = firstRowCols.map((_, idx) => `column-${idx + 1}`);
    }

    return headers.map(h => h.toUpperCase());
  }


// ---------------- Updated buildBindings ----------------
  async buildBindings(): Promise<BindingPayload> {
    const bindings: BindingPayload = {};

    for (const r of this.tableBindings) {
      if (!r.name || !r.path) continue;

      // Ensure columnNames exist
      if (!r.columnNames || r.columnNames.length === 0) {
        r.columnNames = await this.inferColumnNamesFromCsv(r.path, !!r.hasHeader);
      }

      // Only send columns the user actually changed
      const userTypes = this.persistedColumnTypes[r.path] || {};
      const columnTypes: Record<string, string> = {};

      Object.entries(userTypes).forEach(([col, val]) => {
        if (val) {
          columnTypes[col.toUpperCase()] = val.toLowerCase();
        }
      });


      bindings[r.name] = {
        path: r.path,
        hasHeader: !!r.hasHeader,
        columnTypes,              // only user overrides
        inferredTypes: r.inferredTypes || {}, // dynamic inferred types
        customDelimiter: ',',
        fileEncoding: 'UTF_8',
        columnNames: r.columnNames,
      };
    }

    return bindings;
  }



  async openFileSelector(i: number) {
    const row = this.tableBindings[i];

    const modalRef = this.modal.create({
      nzContent: FileSelectionComponent,
      nzWidth: 700,
      nzData: { selectedFilePath: row.path || '' },
    });

    const result = await firstValueFrom(modalRef.afterClose);
    if (result) {
      const file = result as { parentDir: string, name: string, ownerEmail: string };
      const fullPath = [file.parentDir, file.name].filter(Boolean).join('/');
      row.path = fullPath;

      const selectedDatasetFile = parseFilePathToDatasetFile(fullPath);
      if (selectedDatasetFile) {
        const foundDataset = this.datasets.find(
          (d) => d.dataset?.name === selectedDatasetFile.datasetName && d.ownerEmail === selectedDatasetFile.ownerEmail
        );

        if (foundDataset && foundDataset.dataset?.did !== undefined) {
          row.datasetId = foundDataset.dataset.did;

          try {
            // Retrieve versions
            const versions = await firstValueFrom(this.datasetService.retrieveDatasetVersionList(foundDataset.dataset.did));
            row.versions = versions;

            const foundVersion = versions.find((v) => v.name === selectedDatasetFile.versionName);
            if (foundVersion && foundVersion.dvid !== undefined) {
              row.versionId = foundVersion.dvid;

              // Retrieve file tree
              const fileTreeData = await firstValueFrom(
                this.datasetService.retrieveDatasetVersionFileTree(row.datasetId, row.versionId)
              );
              row.files = fileTreeData.fileNodes ? this.getCsvFiles(fileTreeData.fileNodes) : [];

              // ------------------ NEW: Auto-infer column names & types ------------------
              row.columnNames = await this.inferColumnNamesFromCsv(row.path, !!row.hasHeader);

              // Read first few lines to infer types
              const blob = await firstValueFrom(this.datasetService.retrieveDatasetVersionSingleFile(row.path));
              const text = await new Promise<string>((resolve, reject) => {
                const reader = new FileReader();
                reader.onload = () => resolve(reader.result as string);
                reader.onerror = () => reject(reader.error);
                reader.readAsText(blob);
              });

              const lines = text.split(/\r?\n/).filter(l => l.trim() !== '');
              const dataLines = !!row.hasHeader ? lines.slice(1, 11) : lines.slice(0, 10);
              const rows = dataLines.map(l => l.split(','));
              const previewData: Array<Record<string, any>> = rows.map(r =>
                row.columnNames!.reduce((acc, h, idx) => ({ ...acc, [h]: r[idx] ?? '' }), {})
              );

              row.inferredTypes = this.inferCsvScanColumnTypes(previewData);
            }
          } catch (error) {
            console.error('Failed to fetch dataset versions, file tree, or infer columns:', error);
            row.versionId = null;
            row.files = [];
            row.columnNames = [];
            row.inferredTypes = {};
          }
        }
      }
    }
  }




  private loadWorkflowToEditor(workflowContent: any, workflowName: string) {
    const workflow: any = {
      content: workflowContent,
      name: workflowName || 'Imported Workflow',
      readonly: false,
      isPublished: 0,
    };
    this.workflowActionService.enableWorkflowModification();
    this.workflowActionService.reloadWorkflow(workflow, true);
    this.undoRedoService.clearUndoStack();
    this.undoRedoService.clearRedoStack();
  }

  async runQuery(): Promise<void> {
    if (this.isRunDisabled()) {
      alert('Please complete all table rows and enter SQL.');
      return;
    }

    try {
      const bindings = await this.buildBindings();

      const sqlTables = Array.from(
        new Set(
          (this.sqlQuery.match(/\b(from|join)\s+([A-Za-z_][A-Za-z0-9_]*)/gi) || []).map((m) =>
            m.split(' ')[1]
          )
        )
      );

      const missing = sqlTables.filter((t) => !(t in bindings));
      if (missing.length) {
        alert(`No CSV binding found for table(s): ${missing.join(', ')}`);
        return;
      }

      this.isSubmitting = true;
      this.datasetService.convertSqlToWorkflow({ sql: this.sqlQuery.trim(), bindings }).subscribe({
        next: (response: any) => {
          this.isSubmitting = false;
          if (response && response.operators && response.links) {
            this.loadWorkflowToEditor(response, "Imported Workflow");
            this.lastResponse = response;
            this.workflowLoaded = true;
            this.modalRef.destroy();
          } else {
            alert("The backend returned an invalid workflow structure.");
            console.error("Invalid response from backend:", response);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.isSubmitting = false;
          alert(err?.error?.error || err?.message || 'Unknown error');
        },
      });
    } catch (error) {
      console.error('Failed to build bindings:', error);
      alert('Failed to build table bindings. Please try previewing CSV first.');
    }
  }



  trackByKey(index: number, item: any): string {
    return item.key;
  }
}

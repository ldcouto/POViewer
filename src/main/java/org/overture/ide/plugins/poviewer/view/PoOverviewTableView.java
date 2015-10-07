/*
 * #%~
 * org.overture.ide.plugins.poviewer
 * %%
 * Copyright (C) 2008 - 2014 Overture
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #~%
 */
package org.overture.ide.plugins.poviewer.view;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.overture.alloy.VdmToAlloy;
import org.overture.ast.analysis.AnalysisException;
import org.overture.ide.core.ElementChangedEvent;
import org.overture.ide.core.ElementChangedEvent.DeltaType;
import org.overture.ide.core.IElementChangedListener;
import org.overture.ide.core.IVdmElement;
import org.overture.ide.core.IVdmElementDelta;
import org.overture.ide.core.IVdmModel;
import org.overture.ide.core.VdmCore;
import org.overture.ide.core.resources.IVdmProject;
import org.overture.ide.plugins.poviewer.Activator;
import org.overture.ide.plugins.poviewer.IPoviewerConstants;
import org.overture.ide.plugins.poviewer.PoGeneratorUtil;
import org.overture.ide.ui.utility.EditorUtility;
import org.overture.pog.obligation.ProofObligation;
import org.overture.pog.pub.IProofObligation;
import org.overture.pog.pub.POStatus;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod;
import edu.mit.csail.sdg.alloy4viz.VizGUI;

public class PoOverviewTableView extends ViewPart implements ISelectionListener {

	protected TableViewer viewer;
	protected Action doubleClickAction;
	protected Display display = Display.getCurrent();
	protected IVdmProject project;
	protected Action rightClickAction;
	protected VizGUI viz;

	private ViewerFilter provedFilter = new ViewerFilter() {

		@Override
		public boolean select(Viewer viewer, Object parentElement,
				Object element) {
			if (element instanceof ProofObligation
					&& ((ProofObligation) element).status == POStatus.UNPROVED)
				return true;
			else
				return false;
		}

	};
	private Action actionSetProvedFilter;

	protected class ViewContentProvider implements IStructuredContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}

		public void dispose() {
		}

		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof List) {
				@SuppressWarnings("rawtypes")
				List list = (List) inputElement;
				return list.toArray();
			}
			return new Object[0];
		}

	}

	class ViewLabelProvider extends LabelProvider implements
			ITableLabelProvider {

		public void resetCounter() {
			count = 0;
		}

		private Integer count = 0;

		public String getColumnText(Object element, int columnIndex) {
			ProofObligation data = (ProofObligation) element;
			String columnText;
			switch (columnIndex) {
			case 0:
				count++;
				columnText = new Integer(data.number).toString();// count.toString();
				break;
			case 1:
				if (!data.getLocation().getModule().equals("DEFAULT"))
					columnText = data.getLocation().getModule() + "`"
							+ data.name;
				else
					columnText = data.name;
				break;
			case 2:
				columnText = data.kind.toString();
				break;
			case 3:
				columnText = "";// data.status.toString();
				break;
			default:
				columnText = "not set";
			}
			return columnText;

		}

		public Image getColumnImage(Object obj, int index) {
			if (index == 3) {
				return getImage(obj);
			}
			return null;
		}

		@Override
		public Image getImage(Object obj) {
			ProofObligation data = (ProofObligation) obj;

			String imgPath = "icons/cview16/caution.png";

			if (data.status == POStatus.PROVED)
				imgPath = "icons/cview16/proved.png";

			return Activator.getImageDescriptor(imgPath).createImage();
		}

	}

	class IdSorter extends ViewerSorter {
	}

	private IElementChangedListener vdmlistner = new IElementChangedListener() {

		@Override
		public void elementChanged(ElementChangedEvent event) {

			if (event.getType() == DeltaType.POST_RECONCILE) {

				if (event.getDelta().getKind() == IVdmElementDelta.F_TYPE_CHECKED) {

					final IVdmElement source = event.getDelta().getElement();

					final UIJob showJob = new UIJob("Generating Proof Obligations") {

						@Override
						public IStatus runInUIThread(IProgressMonitor monitor) {

							if (source instanceof IVdmModel) {
								IVdmModel castSource = (IVdmModel) source;
								IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
								if (!page.getPerspective().getId().equals(IPoviewerConstants.ProofObligationPerspectiveId))
								{
									return new Status(IStatus.OK,
											"org.overture.ide.plugins.poviewer", "Ok");
								}
								PoGeneratorUtil util = new PoGeneratorUtil(
										display.getActiveShell(), page
												.getActivePart().getSite());

								if (!util.isPoggedModel(castSource)){
									return new Status(IStatus.OK,
											"org.overture.ide.plugins.poviewer", "Ok");
								}
								util.generate(castSource);

								System.out.println("built something");
							}

							if (viewer != null && viewer.getControl() != null
									&& viewer.getControl().getDisplay() != null)
								viewer.getControl().getDisplay()
										.asyncExec(new Runnable() {
											/*
											 * (non-Javadoc)
											 * 
											 * @see java.lang.Runnable#run()
											 */
											public void run() {
												if (!viewer.getControl()
														.isDisposed()) {
													viewer.refresh();
												}
											}
										});

							return new Status(IStatus.OK,
									"org.overture.ide.plugins.poviewer", "Ok");
						}

					};
					showJob.schedule();

				}

			}

		}

	};

	/**
	 * The constructor.
	 */
	public PoOverviewTableView() {
		VdmCore.addElementChangedListener(vdmlistner);
	}

	@Override
	public void dispose() {
		super.dispose();
	VdmCore.removeElementChangedListener(vdmlistner);
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		viewer = new TableViewer(parent, SWT.FULL_SELECTION | SWT.H_SCROLL
				| SWT.V_SCROLL);
		// test setup columns...
		TableLayout layout = new TableLayout();
		layout.addColumnData(new ColumnWeightData(20, true));
		layout.addColumnData(new ColumnWeightData(100, true));
		layout.addColumnData(new ColumnWeightData(60, false));
		layout.addColumnData(new ColumnWeightData(20, false));
		viewer.getTable().setLayout(layout);
		viewer.getTable().setLinesVisible(true);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setSortDirection(SWT.NONE);
		viewer.setSorter(null);

		TableColumn column01 = new TableColumn(viewer.getTable(), SWT.LEFT);
		column01.setText("No.");
		column01.setToolTipText("No.");

		TableColumn column = new TableColumn(viewer.getTable(), SWT.LEFT);
		column.setText("PO Name");
		column.setToolTipText("PO Name");

		TableColumn column2 = new TableColumn(viewer.getTable(), SWT.LEFT);
		column2.setText("Type");
		column2.setToolTipText("Show Type");

		TableColumn column3 = new TableColumn(viewer.getTable(), SWT.CENTER);
		column3.setText("Status");
		column3.setToolTipText("Show status");

		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());

		makeActions();
		contributeToActionBars();
		hookDoubleClickAction();
		hookRightClickAction();

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {

			public void selectionChanged(SelectionChangedEvent event) {

				Object first = ((IStructuredSelection) event.getSelection())
						.getFirstElement();
				if (first instanceof ProofObligation) {
					try {
						IViewPart v = getSite().getPage().showView(
								IPoviewerConstants.PoTableViewId);

						if (v instanceof PoTableView)
							((PoTableView) v).setDataList(project,
									(ProofObligation) first);
					} catch (PartInitException e) {

						e.printStackTrace();
					}
				}

			}
		});
	}

	protected void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();

		fillLocalToolBar(bars.getToolBarManager());
	}

	protected void fillLocalToolBar(IToolBarManager manager) {

		manager.add(actionSetProvedFilter);
		manager.add(rightClickAction); //Add to ToolBar

		// drillDownAdapter.addNavigationActions(manager);
	}

	protected void makeActions() {
		doubleClickAction = new Action() {
			@Override
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection)
						.getFirstElement();
				if (obj instanceof ProofObligation) {
					gotoDefinition((ProofObligation) obj);
					// showMessage(((ProofObligation) obj).toString());
				}
			}

			private void gotoDefinition(ProofObligation po) {
				IFile file = project.findIFile(po.getLocation().getFile());
				if (IVdmProject.externalFileContentType.isAssociatedWith(file
						.getName())) {
					EditorUtility.gotoLocation(
							IPoviewerConstants.ExternalEditorId, file,
							po.getLocation(), po.name);
				} else {
					EditorUtility.gotoLocation(file, po.getLocation(), po.name);
				}

			}
		};

		actionSetProvedFilter = new Action("Filter proved", Action.AS_CHECK_BOX) {
			@Override
			public void run() {
				ViewerFilter[] filters = viewer.getFilters();
				boolean isSet = false;
				for (ViewerFilter viewerFilter : filters) {
					if (viewerFilter.equals(provedFilter))
						isSet = true;
				}
				if (isSet) {
					viewer.removeFilter(provedFilter);

				} else {
					viewer.addFilter(provedFilter);

				}
				if (viewer.getLabelProvider() instanceof ViewLabelProvider)
					((ViewLabelProvider) viewer.getLabelProvider())
							.resetCounter(); // this
												// is
												// needed
												// to
												// reset
												// the
				// numbering
				viewer.refresh();
			}

		};
		
		rightClickAction = new Action() {
			public void run() {
				Object selected = ((IStructuredSelection) viewer.getSelection()).getFirstElement();
				if (selected instanceof ProofObligation) {
					
					ProofObligation po = (ProofObligation) selected;
					
					if (po.getKindString().equals("type invariant satisfiable")) {
						
						//pre-processing to see if the model has natural numbers
						VdmToAlloy pre = new VdmToAlloy(po.getName(), po.getNode().getClass().getSimpleName(), po.getLocation().getFile().toPath().toString());
						
						IInputValidator valScope = new IInputValidator() { //Validates the scope input
							@Override
							public String isValid(String arg0) {
								String res = null;
								try {
									int scope = Integer.parseInt(arg0);
									if (scope < 0)
										res = "Error with input scope!";
								} catch (Exception e) {
									System.err.println("Error with input scope!");
									res = "Error with input scope!";
								}
								return res;
							}};
						
						String scope = "3"; //default scope
						String intScope = "0"; //int scope
						
						InputDialog scope1 = new InputDialog(null, "Alloy Analyser", "Choose your scope: ", "3", valScope);
					    if (scope1.open() == Window.OK) {
					    	scope = scope1.getValue();
					    	
					    	try {
								if (pre.hasNaturalType()) {
									InputDialog scope2 = new InputDialog(null, "Alloy Analyser", "and Int scope: ", "3", valScope);
								    if (scope2.open() == Window.OK) {
								    	intScope = scope2.getValue();
								    }
								}
							} catch (AnalysisException | IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						
					    	VdmToAlloy vtm = new VdmToAlloy(intScope, scope, true, po.getName(), po.getNode().getClass().getSimpleName(), po.getLocation().getFile().toPath().toString());
							try {
								if(vtm.execute() == 1) {
								    System.err.println(vtm.getError());
								    displayMessage("An error occurred", vtm.getError());
								}
								else {
									String filename = vtm.getFilename();
									A4Reporter rep = new A4Reporter() {
						                @Override 
						                public void warning(ErrorWarning msg) {
						                    System.out.print("Warning:\n"+(msg.toString().trim())+"\n\n");
						                    System.out.flush();
						                }
						            };
						            
						            System.out.println("=========== Parsing+Typechecking "+filename+" =============");
						            String run = null;
						
						            try {
						                Module world = CompUtil.parseEverything_fromFile(rep, null, filename);
						                String[] cmds = new String [world.getAllCommands().size()];
						                // Choose some default options for how you want to execute the commands
						                A4Options options = new A4Options();
				
						                options.solver = A4Options.SatSolver.SAT4J;
						                int i = 0;
						                for (Command command : world.getAllCommands()) {
						                	cmds[i] = command.toString();
						                    System.out.println("============ Command " + command + ": ============");
						                    i++;
						                }
						                run = (String)JOptionPane.showInputDialog( //what if cancel?
				                                null,
				                                "Please choose which command you want to run: ",
				                                "Choose Command",
				                                JOptionPane.PLAIN_MESSAGE,
				                                null,
				                                cmds,
				                                cmds[0]);
							            
							            if ((run != null) && (run.length() > 0)) {
							                System.out.println(run);
							            }
							            Command cmnd = null;
							            for (Command command : world.getAllCommands()) {
						                	if (run.toString().equals(command.toString()))
						                		cmnd = command;
						                }
							            
							            final A4Solution ans = TranslateAlloyToKodkod.execute_command(rep, world.getAllReachableSigs(), cmnd, options);
					
										System.out.println("Alloy Translation filepath: " + vtm.getFilename());
										final File fileToOpen = new File(vtm.getFilename());
										
										if (!ans.satisfiable()) {
											displayMessage("Alloy Analyser Outcome", "UNSATISFIABLE");
										}
										else {
											String outcome = run + ans.toString();
											
											MessageDialog result = 
													new MessageDialog(null, "Alloy Analyser Outcome", null,
															outcome, MessageDialog.CONFIRM,
															new String[]{"Open Visualizer", "Get Model", "Close"}, 2) {
												protected void buttonPressed(int buttonId) {
												    setReturnCode(buttonId);
												    switch (buttonId) {
												    	case 0:	
												    		//Open Visualizer
										                    try {
																ans.writeXML("alloy_output.xml");
															} catch (Err e) {
																displayMessage("Error", "Can't write model to file");
																e.printStackTrace();
															}
										                    if (viz==null)
										                        viz = new VizGUI(false, "alloy_output.xml", null);
										                    else
										                        viz.loadXML("alloy_output.xml", true);
												    		break;
												    	case 1:
												    		//Get Model
												    		if (fileToOpen.exists() && fileToOpen.isFile()) {
												    		    IFileStore fileStore = EFS.getLocalFileSystem().getStore(fileToOpen.toURI());
												    		    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
												    		 
												    		    try {
												    		        IDE.openEditorOnFileStore( page, fileStore );
												    		    } catch ( PartInitException e ) {
												    		    	displayMessage("Error", "Can't open Alloy model.");
												    		        e.printStackTrace();
												    		    }
												    		}
												    		break;
												    	case 2:
												    		//Close
												    		close();
												    		break;
												    	default:
												    		close();
												    		break;
												    }
												}};
												result.open();
										}
						            }
						            catch (Exception e) {
						            	e.printStackTrace();
						            }
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					else {
						displayMessage("PO Type", "This PO Type is not supported");
					}
				}
			}
		};
		rightClickAction.setText("Discharge PO with Alloy");
		rightClickAction.setToolTipText("Discharge PO with Alloy Analyser");
		rightClickAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
		getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
	}
	
	private void displayMessage (String title, String message) {
		MessageDialog.openInformation(
				viewer.getControl().getShell(),
				title,
				message);
	}

	protected void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}
	
	protected void hookRightClickAction() { //Add to rightClick
		MenuManager manager = new MenuManager();
		viewer.getControl().setMenu(manager.createContextMenu(viewer.getControl()));
		manager.add(rightClickAction);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	public void selectionChanged(IWorkbenchPart part, ISelection selection) {

		if (selection instanceof IStructuredSelection
				&& part instanceof PoOverviewTableView) {
			Object first = ((IStructuredSelection) selection).getFirstElement();
			if (first instanceof ProofObligation) {
				try {
					IViewPart v = part
							.getSite()
							.getPage()
							.showView(
									"org.overture.ide.plugins.poviewer.views.PoTableView");

					if (v instanceof PoTableView)
						((PoTableView) v).setDataList(project,
								(ProofObligation) first);
				} catch (PartInitException e) {

					e.printStackTrace();
				}
			}
		}

	}

	public void refreshList() {
		display.asyncExec(new Runnable() {

			public void run() {
				viewer.refresh();
			}

		});
	}

	public void setDataList(final IVdmProject project,
			final List<IProofObligation> data) {
		this.project = project;
		display.asyncExec(new Runnable() {

			public void run() {
				if (viewer.getLabelProvider() instanceof ViewLabelProvider)
					((ViewLabelProvider) viewer.getLabelProvider())
							.resetCounter(); // this
												// is
												// needed
												// to
												// reset
												// the
				// numbering

				viewer.setInput(data);

				for (TableColumn col : viewer.getTable().getColumns()) {
					col.pack();
				}
			}

		});
	}
}

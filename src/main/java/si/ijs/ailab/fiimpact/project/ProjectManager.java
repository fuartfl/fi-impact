package si.ijs.ailab.fiimpact.project;


import javax.servlet.ServletOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.*;

//import org.apache.xpath.operations.String;
import com.opencsv.CSVReader;
import org.json.JSONWriter;
import org.xml.sax.SAXException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import si.ijs.ailab.fiimpact.indicators.OverallResult;
import si.ijs.ailab.fiimpact.settings.FIImpactSettings;
import si.ijs.ailab.fiimpact.settings.IOListDefinition;
import si.ijs.ailab.fiimpact.settings.IOListField;

import java.util.*;

/**
 * Created by flavio on 01/06/2015.
 */




public class ProjectManager
{
  private Map<String, MattermarkIndicatorInfo> mattermarkIndicatorInfoMap = new HashMap<>();
  private Map<String, OverallResult.ScoreBoundaries> mattermarkSpeedometerSlots = new HashMap<>();

  public Map<String, ProjectData> getProjects()
  {
    return projects;
  }

  private final Map<String, ProjectData> projects = Collections.synchronizedMap(new HashMap<String, ProjectData>());

  private final static Logger logger = LogManager.getLogger(ProjectManager.class.getName());

  public static class MattermarkIndicatorInfo
  {
    public IOListField getIoListField()
    {
      return ioListField;
    }

    IOListField ioListField;
    double min;
    double max;
    int count = 0;

    MattermarkIndicatorInfo(IOListField ioListField)
    {
      this.ioListField = ioListField;
    }

    public String getId()
    {
      return ioListField.getFieldid();
    }

    public double getMin()
    {
      return min;
    }

    public double getMax()
    {
      return max;
    }

    public int getCount()
    {
      return count;
    }
  }


  public ProjectManager()
  {
    loadProjects();
  }

  private void loadProjects()
  {
    logger.info("Load id list from: {}", FIImpactSettings.getFiImpactSettings().getProjectsList().toString());
    projects.clear();
    try
    {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(FIImpactSettings.getFiImpactSettings().getProjectsList().toFile()), "utf-8"));
      String line = br.readLine();
      while(line != null)
      {
        ProjectData pd = loadProject(line);
        if(pd != null)
        {
          projects.put(line, pd);
        }
        else
          logger.error("Project {} does not exist.", line);
        line = br.readLine();
      }
      br.close();
      logger.info("loaded");
    }
    catch (IOException ioe)
    {
      logger.error("could not read text file " + FIImpactSettings.getFiImpactSettings().getProjectsList().toString());
    }
    recalcMattermarkIndicatorsInfo();

  }


  private void saveProjectsList()
  {
    logger.info("Saving id list to: {}", FIImpactSettings.getFiImpactSettings().getProjectsList().toString());

    try
    {
      synchronized(projects)
      {
        OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(FIImpactSettings.getFiImpactSettings().getProjectsList().toFile()), "utf-8");
        for(String id : projects.keySet())
        {
          w.write(id + "\n");
        }
        w.close();
      }
      logger.info("Saved: {}", FIImpactSettings.getFiImpactSettings().getProjectsList().toFile());
    }
    catch (IOException ioe)
    {
      logger.error("error writing file", ioe);
    }

  }

  private ProjectData loadProject(String id)
  {

    Path p = FIImpactSettings.getFiImpactSettings().getProjectsRoot().resolve("project-" + id + ".xml");
    ProjectData pd = new ProjectData();

    try
    {
      pd.read(new FileInputStream(p.toFile()));
    }
    catch (ParserConfigurationException | IOException | SAXException e)
    {
      logger.error("Cannot load project data {}", p.toString());
      pd = null;
    }
    return pd;
  }

/*
  public synchronized void addProject(OutputStream outputStream, String[] arrFields, String id) throws IOException
  {
    ProjectData pd = projects.get(id);
    if(pd == null)
    {
      pd = new ProjectData();
      pd.setId(id);
      projects.put(id, pd);
      saveProjectsList();
    }

    pd.addFields(arrFields);
    pd.save(projectsRoot);
    pd.write(outputStream);
  }

  public synchronized void removeProject(ServletOutputStream outputStream, String id)
  {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = null;
    try
    {
      db = dbf.newDocumentBuilder();
    }
    catch (ParserConfigurationException e)
    {
      logger.error("Can't believe this", e);
    }
    Document doc = db.newDocument();
    Element root = doc.createElement("project");
    doc.appendChild(root);
    root.setAttribute("id", id);
    ProjectData pd = projects.get(id);

    if(pd==null)
    {
      root.setTextContent("Project not found, can't delete");
      logger.error("Project not found, can't delete: {}", id);
    }
    else
    {
      Path p = projectsRoot.resolve("project-" + id + ".xml");
      p.toFile().delete();
      root.setTextContent("Project removed.");
      logger.info("Project removed: {}", id);
      projects.remove(id);
      saveProjectsList();
    }
    AIUtils.save(doc, outputStream);
  }

*/

  private static final int ADD_STATUS_NEW = 0;
  private static final int ADD_STATUS_UPDATE = 1;
  private static final int ADD_STATUS_SKIP = 2;

  //returns the status defined above
  private int addProject(int idIndex, ArrayList<IOListField> orderListDefinition, String[] fields)
  {
    int ret = ADD_STATUS_UPDATE;
    boolean skipProject = false;// false=save project;true=skip project
    // clean missing, and include record if yes
    for(int i = 0; i < fields.length; i++)
    {
      IOListField ioListField = orderListDefinition.get(i);
      if(ioListField!=null)
      {
        if(ioListField.getMissing().length() != 0)
        {
          for(String missing : ioListField.getMissing().split(";"))
            if(missing.equals(fields[i]))
            {
              fields[i] = "";
              break;
            }
        }

        if(ioListField.getInclude_record_when().length() != 0)
          if(!ioListField.getInclude_record_when().toLowerCase().equals(fields[i].toLowerCase()))
          {
            skipProject = true;
            break;
          }
      }
    }

    if(skipProject)
    {
      ret = ADD_STATUS_SKIP;
    }
    else
    {
      String id = fields[idIndex];
      ProjectData pd = projects.get(id);
      if(pd == null)
      {
        ret = ADD_STATUS_NEW;
        pd = new ProjectData();
        pd.setId(id);
        projects.put(id, pd);
        saveProjectsList();
      }

      pd.addFields(orderListDefinition, fields);
      pd.save(FIImpactSettings.getFiImpactSettings().getProjectsRoot());
    }
    return ret;
  }

  //returns the status defined above
  private int addMapping(int idIndex, int urlIndex, String[] fields)
  {
    int ret = ADD_STATUS_UPDATE;
    boolean skipProject = false;// false=save project;true=skip project
    String id = fields[idIndex];
    String url = fields[urlIndex];
    if(id==null || id.equals(""))
          skipProject = true;

    if(skipProject)
    {
      ret = ADD_STATUS_SKIP;
      logger.error("Project id empty: {}", id);
    }
    else
    {
      ProjectData pd = projects.get(id);
      if(pd == null)
      {
        logger.error("Project {} not found", id);
        ret = ADD_STATUS_SKIP;
      }
      else
      {
        //logger.debug("{}={}", getListDefinition(LIST_PROJECTS).usageCleanUrl.getFieldid(), url);
        pd.setValue(FIImpactSettings.getFiImpactSettings().getListDefinition(FIImpactSettings.LIST_PROJECTS).getUsageCleanUrl().getFieldid(), url);
        pd.save(FIImpactSettings.getFiImpactSettings().getProjectsRoot());
      }
    }
    return ret;
  }

  // import according to the definition in the file lists-io-def.xml, <list name="project-list">
  public void importProjects(ServletOutputStream outputStream, Path p) throws IOException
  {
    projects.clear();
    logger.info("Load data from {}", p.toString());

    OutputStreamWriter w = new OutputStreamWriter(outputStream, "utf-8");
    JSONWriter json = new JSONWriter(w);
    JSONWriter array = json.array();
    array.object().key("total_before").value(projects.size()).endObject();

    IOListDefinition ioListDefinition = FIImpactSettings.getFiImpactSettings().getListDefinition(FIImpactSettings.LIST_PROJECTS);
    Map<String, IOListField> ioListDefinitionFields = ioListDefinition.getFieldsByColumn();

    StringBuilder sbErrorColumnsNotDefined = new StringBuilder();
    StringBuilder sbErrorColumnsNotImported = new StringBuilder();
    int idIndex = -1;
    boolean bImportCanceled = false;
    int[] projectCounters = new int[3];

    CSVReader reader = new CSVReader(new FileReader(p.toFile()), ';', '"');
    List<String[]> csvLines = reader.readAll();
    logger.info("Loaded {} rows. Start at {}", csvLines.size(), ioListDefinition.getStartAtRow());
    if(csvLines.size() > ioListDefinition.getStartAtRow())
    {
      for(int iLine = 0; iLine < ioListDefinition.getStartAtRow(); iLine++)
      {
        logger.debug(Arrays.toString(csvLines.get(iLine)));
      }
      ArrayList<IOListField> importOrderListDefinitionFields;
      String[] header = csvLines.get(ioListDefinition.getStartAtRow());
      logger.debug(Arrays.toString(header));
      // mapping header and definition
      importOrderListDefinitionFields = new ArrayList<>();
      Set<String> importedFieldsIds = new TreeSet<>();
      for(int i = 0; i < header.length; i++)
      {
        String attribute = header[i];
        IOListField ioListField = ioListDefinitionFields.get(attribute);
        importOrderListDefinitionFields.add(ioListField);
        if(ioListField == null)
        {
          logger.error("Projects import - column header {} not defined", attribute);
          if(sbErrorColumnsNotDefined.length() > 0)
            sbErrorColumnsNotDefined.append(", ");
          sbErrorColumnsNotDefined.append(attribute);
        }
        else
        {
          importedFieldsIds.add(ioListField.getFieldid());
          if(ioListField.getUsage().equals("id"))
            idIndex = i;

        }
      }

      for(IOListField ioListField : ioListDefinitionFields.values())
      {
        if(!importedFieldsIds.contains(ioListField.getFieldid()))
        {
          if(sbErrorColumnsNotImported.length() > 0)
            sbErrorColumnsNotImported.append(", ");

          String usage = ioListField.getUsage();
          if(usage != null && usage.equals("id"))
          {
            bImportCanceled = true;
            logger.error("Cancel import - {} not defined.", ioListField.getColumn());
            sbErrorColumnsNotImported.append(ioListField.getColumn()).append(" (").append(usage).append(")");
          }
          else
            sbErrorColumnsNotImported.append(ioListField.getColumn());
        }
      }
      if(!bImportCanceled)
      {
        for(int iLine = ioListDefinition.getStartAtRow() + 1; iLine < csvLines.size(); iLine++)
        {

          projectCounters[addProject(idIndex, importOrderListDefinitionFields, csvLines.get(iLine))]++;

        }
      }
    }
    logger.info("Projects: added={}; updated={}; total={}.", projectCounters[ADD_STATUS_NEW], projectCounters[ADD_STATUS_UPDATE], projects.size());
    logger.info("Skipped {} projects", projectCounters[ADD_STATUS_SKIP]);
    array.object().key("total_added").value(projectCounters[ADD_STATUS_NEW]).endObject();
    array.object().key("total_updated").value(projectCounters[ADD_STATUS_UPDATE]).endObject();
    array.object().key("total_skipped").value(projectCounters[ADD_STATUS_SKIP]).endObject();
    array.object().key("total_after").value(projects.size()).endObject();

    array.endArray();

    recalcMattermarkIndicatorsInfo();

    w.flush();
    w.close();

  }
  // import according to the definition in the file lists-io-def.xml, <list name="project-list">
  public void importMappings(ServletOutputStream outputStream, Path p) throws IOException
  {
    logger.info("Load data from {}", p.toString());

    OutputStreamWriter w = new OutputStreamWriter(outputStream, "utf-8");
    JSONWriter json = new JSONWriter(w);
    JSONWriter array = json.array();
    array.object().key("total_before").value(projects.size()).endObject();

    IOListDefinition ioListDefinition = FIImpactSettings.getFiImpactSettings().getListDefinition(FIImpactSettings.LIST_MAPPING);
    Map<String, IOListField> ioListDefinitionFields = ioListDefinition.getFieldsByColumn();

    StringBuilder sbErrorColumnsNotImported = new StringBuilder();
    boolean bImportCanceled = false;
    int[] projectCounters = new int[3];
    int idIndex = -1;
    int urlIndex = -1;

    CSVReader reader = new CSVReader(new FileReader(p.toFile()), ';', '"');
    List<String[]> csvLines = reader.readAll();
    if(csvLines.size() > ioListDefinition.getStartAtRow())
    {
      ArrayList<IOListField> importOrderListDefinitionFields;
      String[] header = csvLines.get(ioListDefinition.getStartAtRow());
      // mapping header and definition
      importOrderListDefinitionFields = new ArrayList<>();
      Set<String> importedFieldsIds = new TreeSet<>();
      for(int i = 0; i < header.length; i++)
      {
        String attribute = header[i];
        IOListField ioListField = ioListDefinitionFields.get(attribute);
        importOrderListDefinitionFields.add(ioListField);
        if(ioListField != null)
        {
          importedFieldsIds.add(ioListField.getFieldid());
          if(ioListField.getUsage().equals("id"))
            idIndex = i;
          else if(ioListField.getUsage().equals("clean-url"))
          {
            urlIndex = i;
          }
        }
      }
      if(urlIndex == -1 || idIndex == -1)
      {
        sbErrorColumnsNotImported.append("FI-IMPACT id and clean URL required.");
        logger.error("FI-IMPACT id and clean URL required.");
        bImportCanceled = true;
      }
      logger.info("Parsed header with {} fileds, canceled={}, lines_total={}. url index={}, idIndex={}", importOrderListDefinitionFields.size(), bImportCanceled, csvLines.size(), urlIndex, idIndex);

      if(!bImportCanceled)
      {
        for(int iLine = ioListDefinition.getStartAtRow() + 1; iLine < csvLines.size(); iLine++)
        {
          //logger.debug("Line: {}", Arrays.toString(csvLines.get(iLine)));
          projectCounters[addMapping(idIndex, urlIndex, csvLines.get(iLine))]++;

        }
      }
    }
    logger.info("URLs: set={}; total projects={}.", projectCounters[ADD_STATUS_NEW], projects.size());
    logger.info("Skipped {} projects", projectCounters[ADD_STATUS_SKIP]);
    array.object().key("total_set").value(projectCounters[ADD_STATUS_NEW]).endObject();
    array.object().key("total_updated").value(projectCounters[ADD_STATUS_UPDATE]).endObject();
    array.object().key("total_skipped").value(projectCounters[ADD_STATUS_SKIP]).endObject();
    array.object().key("total_after").value(projects.size()).endObject();

    array.endArray();

    recalcMattermarkIndicatorsInfo();

    w.flush();
    w.close();

  }

  private boolean addProjectMattermark(Map<String, ProjectData> projectDataByURL, ArrayList<IOListField> importOrderListDefinitionFields, String[] fields)
  {

    String cleanProjectUrlFromMattermark = null;
    boolean ret = false;
    // clean missing
    for(int i = 0; i < fields.length; i++)
    {
      IOListField ioListField = importOrderListDefinitionFields.get(i);
      if(ioListField != null)
      {
        if(ioListField.getMissing().length() != 0)
        {
          for(String missing : ioListField.getMissing().split(";"))
            if(missing.equals(fields[i]))
            {
              fields[i] = "";
              break;
            }
        }


        if(ioListField.getUsage().length() != 0)
          if(ioListField.getUsage().equals("clean-url"))
          {
            cleanProjectUrlFromMattermark = fields[i];
          }
      }
    }

    if(cleanProjectUrlFromMattermark != null)
    {
      ProjectData projectData = projectDataByURL.get(cleanProjectUrlFromMattermark);
      if(projectData != null)
      {
        ret = true;
        projectData.addFieldsMattermark(importOrderListDefinitionFields, fields);
        projectData.save(FIImpactSettings.getFiImpactSettings().getProjectsRoot());
      }
      else
        logger.warn("Cant find project data for {}", cleanProjectUrlFromMattermark);

    }
    else
      logger.warn("clean project URL from Mattermark is null");
    return ret;
  }

  //import Mattermark data
  public void importMattermark(ServletOutputStream outputStream, Path p) throws IOException
  {
    logger.info("Load data from {}", p.toString());

    OutputStreamWriter w = new OutputStreamWriter(outputStream, "utf-8");
    JSONWriter json = new JSONWriter(w);
    JSONWriter array = json.array();
    array.object().key("Total_number_of_projects").value(projects.size()).endObject();

    IOListDefinition ioListDefinition = FIImpactSettings.getFiImpactSettings().getListDefinition(FIImpactSettings.LIST_MATTERMARK);
    Map<String, IOListField> ioListDefinitionFields = ioListDefinition.getFieldsByColumn();

    StringBuilder sbErrorColumnsNotDefined = new StringBuilder();
    StringBuilder sbErrorColumnsNotImported = new StringBuilder();
    boolean bImportCanceled = false;

     CSVReader reader = new CSVReader(new FileReader(p.toFile()), ',', '"');
    List<String[]> csvLines = reader.readAll();
    if(csvLines.size() > ioListDefinition.getStartAtRow())
    {
      ArrayList<IOListField> importOrderListDefinitionFields;
      int mattermarkCounter = 0;// number addded mattermark projects


      /*
       * 2.load the file - use the clean-url usage information to match it
       * with the correct ProjectData instance You may create a temporary map,
       * where you have the clean-url as key in order to find the correct
       * project. Save each ProjectData instance and the list.
       */
      // clean-url
      IOListDefinition ioListDefinitionProjects = FIImpactSettings.getFiImpactSettings().getListDefinition(FIImpactSettings.LIST_PROJECTS);
      Map<String, ProjectData> projectDataByURL = new HashMap<>();
      IOListField projectURLField = ioListDefinitionProjects.getUsageCleanUrl();

      for(ProjectData projectData : projects.values())
      {
        String projectURL = projectData.getValue(projectURLField.getFieldid());
        if(projectURL != null)
        {
          projectDataByURL.put(projectURL, projectData);
        }
      }


      String[] header = csvLines.get(ioListDefinition.getStartAtRow());
      // mapping header and definition
      importOrderListDefinitionFields = new ArrayList<>();
      Set<String> importedFieldsIds = new TreeSet<>();
      for(String attribute : header)
      {
        IOListField ioListField = ioListDefinitionFields.get(attribute);
        importOrderListDefinitionFields.add(ioListField);
        if(ioListField == null)
        {
          logger.error("Mattemrark import - column header %s not defined", attribute);
          if(sbErrorColumnsNotDefined.length() > 0)
            sbErrorColumnsNotDefined.append(", ");
          sbErrorColumnsNotDefined.append(attribute);
        }
        else
          importedFieldsIds.add(ioListField.getFieldid());
      }

      for(IOListField ioListField : ioListDefinitionFields.values())
      {
        if(!importedFieldsIds.contains(ioListField.getFieldid()))
        {
          if(sbErrorColumnsNotImported.length() > 0)
            sbErrorColumnsNotImported.append(", ");

          String usage = ioListField.getUsage();
          if(usage != null && !usage.equals(""))
          {
            bImportCanceled = true;
            sbErrorColumnsNotImported.append(ioListField.getColumn()).append(" (").append(usage).append(")");
          }
          else
            sbErrorColumnsNotImported.append(ioListField.getColumn());
        }
      }
      if(!bImportCanceled)
      {
        // 1. go through all ProjectData instances and clear mattermark information
        for(String key : projects.keySet())
        {
          ProjectData projectData = projects.get(key);
          projectData.getMattermarkFields().clear();
        }

      }
      else
        logger.error("Mattermark import canceled due to corrupted import file - check UI info");

      if(!bImportCanceled)
      {
        for(int iLine = ioListDefinition.getStartAtRow()+1; iLine < csvLines.size(); iLine++)
        {

          if(addProjectMattermark(projectDataByURL, importOrderListDefinitionFields, csvLines.get(iLine)))
            mattermarkCounter++;
        }
      }

      logger.info("Added mattermark {} projects, total {}.", mattermarkCounter, projects.size());
      array.object().key("Added_mattermark_company_information").value(mattermarkCounter).endObject();
      if(sbErrorColumnsNotDefined.length() > 0)
        array.object().key("Warning").value("The following Mattermark columns are unknown to FI-Impact - they are ignored: " + sbErrorColumnsNotDefined.toString()).endObject();

      if(bImportCanceled)
        array.object().key("Error").value("The following columns are not provided by Mattermark: " + sbErrorColumnsNotImported.toString()).endObject();
      else if(sbErrorColumnsNotImported.length() > 0)
        array.object().key("Warning").value("The following columns are not provided by Mattermark: " + sbErrorColumnsNotImported.toString()).endObject();


      if(bImportCanceled)
        array.object().key("Import canceled").value("The import was canceled due to a fatal error and previous Mattermark data has been retained").endObject();
    }
    array.endArray();

    logger.info("Recalculate mattermark indicators");
    recalcMattermarkIndicatorsInfo();
    logger.info("Recalculate survey scores");
    FIImpactSettings.getFiImpactSettings().getSurveyManager().recalcSurveyResults();
    logger.info("Recalculate overall results");
    FIImpactSettings.getFiImpactSettings().getSurveyManager().recalcResults();
    logger.info("Done");
    w.flush();
    w.close();

  }


  synchronized public void clearAllProjects(ServletOutputStream outputStream) throws IOException
  {
    OutputStreamWriter w = new OutputStreamWriter(outputStream, "utf-8");
    logger.info("Remove all {} projects", projects.size());
    JSONWriter json = new JSONWriter(w);
    json.object().key("total").value(projects.size());
    synchronized(projects)
    {
      for(String id : projects.keySet())
      {
        Path p = FIImpactSettings.getFiImpactSettings().getProjectsRoot().resolve("project-" + id + ".xml");
        p.toFile().delete();
        logger.info("Project removed: {}", id);
      }
    }
    projects.clear();
    saveProjectsList();
    recalcMattermarkIndicatorsInfo();
    json.endObject();
    w.flush();
    w.close();
  }

  private void recalcMattermarkIndicatorsInfo()
  {
    logger.info("recalc Mattermark Indicators Info");
    mattermarkIndicatorInfoMap.clear();
    ArrayList<IOListField> mattermarkIndicators = FIImpactSettings.getFiImpactSettings().getMattermarkIndicators();
    for(IOListField ioListField: mattermarkIndicators)
      mattermarkIndicatorInfoMap.put(ioListField.getFieldid(), new MattermarkIndicatorInfo(ioListField));

    for(ProjectData pd: projects.values())
    {
      for(Map.Entry<String, MattermarkIndicatorInfo> miEntry: mattermarkIndicatorInfoMap.entrySet())
      {
        if (pd.isMattermarkValueSet(miEntry.getKey()))
        {
          MattermarkIndicatorInfo indicatorInfo = miEntry.getValue();
          int mattermarkValue = pd.getMattermarkIntValue(miEntry.getKey());
          if(indicatorInfo.count == 0)
          {
            indicatorInfo.max = mattermarkValue;
            indicatorInfo.min = mattermarkValue;
          }
          else
          {
            if(mattermarkValue < indicatorInfo.min)
              indicatorInfo.min = mattermarkValue;
            if(mattermarkValue > indicatorInfo.min)
              indicatorInfo.max = mattermarkValue;
          }
          indicatorInfo.count++;
        }
      }
    }


    for(Map.Entry<String, MattermarkIndicatorInfo> miEntry: mattermarkIndicatorInfoMap.entrySet())
    {
      MattermarkIndicatorInfo indicatorInfo = miEntry.getValue();
      if(indicatorInfo.ioListField.isTransformLog())
      {
        indicatorInfo.max = Math.signum(indicatorInfo.max)*Math.log(Math.abs(indicatorInfo.max)+1.0);
        indicatorInfo.min = Math.signum(indicatorInfo.min)*Math.log(Math.abs(indicatorInfo.min)+1.0);
      }
    }


    mattermarkSpeedometerSlots.clear();
    for(MattermarkIndicatorInfo mattermarkIndicatorInfo: mattermarkIndicatorInfoMap.values())
    {
      OverallResult.ScoreBoundaries boundaries = new OverallResult.ScoreBoundaries();
      boundaries.setMin(0.0);
      boundaries.setLo_med(1.667);
      boundaries.setMed_hi(3.333);
      boundaries.setMax(5.000);
      mattermarkSpeedometerSlots.put(mattermarkIndicatorInfo.getId(), boundaries);
    }
    logger.info("recalc Mattermark Indicators Info done: {}", mattermarkIndicatorInfoMap.size());
  }

  public MattermarkIndicatorInfo getMattermarkIndicatorInfo(String fieldId)
  {
    return mattermarkIndicatorInfoMap.get(fieldId);
  }


	// Entries are mattemrak "indicator"
  public Map<String, OverallResult.ScoreBoundaries> getMattemrarkSlots()
  {
    return mattermarkSpeedometerSlots;
  }


  public static void main(String[] args) throws Exception
  {
  }



}

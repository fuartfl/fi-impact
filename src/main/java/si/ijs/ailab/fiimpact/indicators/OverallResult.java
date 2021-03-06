package si.ijs.ailab.fiimpact.indicators;

import org.json.JSONArray;
import org.json.JSONObject;
import si.ijs.ailab.fiimpact.settings.FIImpactSettings;
import si.ijs.ailab.fiimpact.survey.SurveyData;
import si.ijs.ailab.fiimpact.survey.SurveyManager;
import si.ijs.ailab.util.AIStructures;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by flavio on 08/09/2015.
 */
public class OverallResult
{

  String id;
  String type;
  int n;
  double sum;
  double average;

  public static class ScoreBoundaries
  {
    double min, lo_med, med_hi, max;

    public double getMin()
    {
      return min;
    }

    public void setMin(double min)
    {
      this.min = min;
    }

    public double getLo_med()
    {
      return lo_med;
    }

    public void setLo_med(double lo_med)
    {
      this.lo_med = lo_med;
    }

    public double getMed_hi()
    {
      return med_hi;
    }

    public void setMed_hi(double med_hi)
    {
      this.med_hi = med_hi;
    }

    public double getMax()
    {
      return max;
    }

    public void setMax(double max)
    {
      this.max = max;
    }
  }

  public class ResultGraph
  {
    String id;
    ScoreBoundaries boundaries;
    public ArrayList<AIStructures.AIInteger> graphValues;

    ResultGraph(String _id, ScoreBoundaries _scoreBoundaries)
    {
      id = _id;
      boundaries = _scoreBoundaries;
      graphValues = new ArrayList<>();
      for (int i = 0; i < 5; i++)
      {
        AIStructures.AIInteger val = new AIStructures.AIInteger();
        val.val = 0;
        graphValues.add(val);
      }
    }


    private void add(ArrayList<SurveyData> surveys)
    {
      for (SurveyData sd : surveys)
        add(sd);
    }

    private void add(SurveyData surveyData)
    {
      int slot;
      Double score = surveyData.results.get(id);
      if (score == null)
        score = -1.0;

      slot = getSlot(score);
      AIStructures.AIInteger cnt = graphValues.get(slot);
      cnt.val++;

      double  dPercentResult = getSpeedometerPercent(score);


      surveyData.resultDerivatives.put(id + "_GRAPH_SLOT", (double) slot);
      surveyData.resultDerivatives.put(id + "_GRAPH_PERCENT", dPercentResult);

    }

    private int getSlot(double d)
    {
      int slot;
      if (d < boundaries.min)
        slot = 0;
      else if (d <= boundaries.lo_med)
        slot = 1;
      else if (d <= boundaries.med_hi)
        slot = 2;
      else if (d <= boundaries.max)
        slot = 3;
      else
        slot = 4;
      return slot;
    }


  }

  ArrayList<SurveyData> surveys;
  public ResultGraph graph;

  public OverallResult(String _type, String _id, ScoreBoundaries scoreBoundaries)
  {
    id = _id;
    n = 0;
    average = 0.0;
    sum = 0.0;
    type = _type;

    graph = new ResultGraph(id, scoreBoundaries);
    surveys = new ArrayList<>();
  }
  public double getSpeedometerPercent(double d)
  {
    if(d <= graph.boundaries.min)
      return 0.0;
    else if(d >= graph.boundaries.max)
      return 1.0;
    else
      return (d-graph.boundaries.min)/(graph.boundaries.max-graph.boundaries.min);
  }

  public double getSpeedometerPercentLM()
  {
    return getSpeedometerPercent(graph.boundaries.lo_med);
  }
  public double getSpeedometerPercentMH()
  {
    return getSpeedometerPercent(graph.boundaries.med_hi);
  }

  public double getMinScore()
  {
    return graph.boundaries.min;
  }

  public double getMaxScore()
  {
    return graph.boundaries.max;
  }

  public void add(SurveyData sd)
  {

    Double r = sd.results.get(id);
    if (r != null)
      surveys.add(sd);
  }

  public void calculate()
  {

    Collections.sort(surveys, new SurveyManager.SurveyDataComparator(id));
    n = surveys.size();
    average = 0.0;
    sum = 0.0;

    int beforeYou = 0;
    int sameAsYou = 0;
    double beforeYouResult = -1.0;

    for (SurveyData sd : surveys)
    {
      Double r = sd.results.get(id);
      sum += r;

      //logger.debug("score: {}", sd.results.get(resultType));
      double percent = Math.round((((double) beforeYou) / n) * 100);
      sd.resultDerivatives.put(id + "_"+type+"_R", percent);
      Double yourResult = sd.results.get(id);
      if (yourResult == null)
        yourResult = 0.0;

      if (beforeYouResult == yourResult)
      {
        sameAsYou++;
      } else
      {
        beforeYou = beforeYou + sameAsYou + 1;
        sameAsYou = 0;
        beforeYouResult = yourResult;
      }

    }

    average = sum / (double) n;
    graph.add(surveys);
  }

  public JSONObject toJSON()
  {
    JSONObject jsonAverage = new JSONObject();
    jsonAverage.put("id", id);
    jsonAverage.put("average", FIImpactSettings.getDecimalFormatter4().format(average));
    jsonAverage.put("average_slot", graph.getSlot(average));
    JSONArray jsonHistogram = toJSONHistogram();
    jsonAverage.put("histogram", jsonHistogram);
    return jsonAverage;
  }

  public JSONArray toJSONHistogram()
  {
    JSONArray jsonHistogram = new JSONArray();
    for (AIStructures.AIInteger cnt : graph.graphValues)
      jsonHistogram.put(cnt.val);
    return jsonHistogram;

  }

  public int getAverageSlot()
  {
    return graph.getSlot(average);
  }

  public String getId()
  {
    return id;
  }

  public String getType()
  {
    return type;
  }

  public int getN()
  {
    return n;
  }

  public double getSum()
  {
    return sum;
  }

  public double getAverage()
  {
    return average;
  }
}

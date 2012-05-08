package com.xtreme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JSONPerfEval extends Activity {

	public static final int NUM_RUNS = 1;
	private static final String QUERY_KEY = "phone";

	private enum JSONParsers {
		JSON_PARSER_JSON_ORG, JSON_PARSER_GSON
		// , JSON_PARSER_JACKSON
	}

	private enum JSONInputs {
		JSON_INPUT_LEVEL6, JSON_INPUT_LEVEL10,
		// JSON_INPUT_TWITTER,
		// JSON_INPUT_FB,
	}

	ArrayList<String> jsonInputData = null;
	ArrayList<String> jsonInputDataFiles = null;

	ProgressBar pb = null;

	public long TEST_JSON_ORG_TIME_NUMELEMS = 0L;
	public long TEST1_JSON_ORG_TIME = 0L;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout lLayout = (LinearLayout) inflater.inflate(R.layout.main, null);
		pb = (ProgressBar) lLayout.findViewById(R.id.test_progress);
		pb.setProgress(0);

		setContentView(lLayout);

		// initialize and set data files
		jsonInputDataFiles = new ArrayList<String>(JSONInputs.values().length);
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_LEVEL6.ordinal(), "small_data.txt");
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_LEVEL10.ordinal(), "random10levels.txt");

		jsonInputData = new ArrayList<String>(JSONInputs.values().length);
		for (JSONInputs input : JSONInputs.values()) {
			jsonInputData.add(input.ordinal(), "");
		}

		// run supported parsers
		RunJSONOrgParsers(JSONInputs.JSON_INPUT_LEVEL6);
	}

	private void RunJSONOrgParsers(JSONInputs inputType) {
		load_data(inputType);

		for (JSONParsers parser : JSONParsers.values()) {
			long start_time = 0L, start_JSONParse = 0L, end_JSONParse = 0L, end_JSONParseQuery = 0L;
			String parserName = "";
			long totalRunTime = 0L, totalParseTime = 0L;

			for (int i = 0; i < NUM_RUNS; i++) {
				start_time = Debug.threadCpuTimeNanos();

				switch (parser) {
				case JSON_PARSER_JSON_ORG: {
					parserName = "JSON_ORG";

					try {
						JSONObject root = new JSONObject(jsonInputData.get(inputType.ordinal()));
						end_JSONParseQuery = Debug.threadCpuTimeNanos();
						JSONArray sessions = root.getJSONArray("result");

						if (sessions != null) {
							start_JSONParse = Debug.threadCpuTimeNanos();
							int hitCount = LocateKey(sessions, QUERY_KEY);
						}
					} catch (Exception e) {
						Log.w("RunJSONOrgParsers", "Exception for parser JSON_ORG " + e.getMessage());
					}
					break;
				}

				case JSON_PARSER_GSON: {
					parserName = "GSON";
					JsonParser jsonParser = new JsonParser();
					JsonElement resultElement = jsonParser.parse(jsonInputData.get(inputType.ordinal()));
					end_JSONParseQuery = Debug.threadCpuTimeNanos();
					JsonObject resultObject = resultElement.getAsJsonObject();
					JsonElement totalElem = resultObject.get("result");
					start_JSONParse = Debug.threadCpuTimeNanos();
					if (totalElem.isJsonArray()) {
						LocateKey(totalElem.getAsJsonArray(), QUERY_KEY);
					}

					break;
				}

				default:
					break;
				} // switch

				end_JSONParse = Debug.threadCpuTimeNanos();
				totalRunTime += (end_JSONParse - start_time) / (long) 1000000.0;
				totalParseTime += (end_JSONParse - start_JSONParse) / (long) 1000000.0;
			} // for
			totalRunTime /= NUM_RUNS;
			totalParseTime /= NUM_RUNS;

			Log.w("TEST_LEVEL6", String.format("%s Total RUNTIME - " + totalRunTime, parserName) + " ms.");
			Log.w("TEST_LEVEL6", String.format("%s Initialization RUNTIME - " + (end_JSONParseQuery - start_time) / (long) 1000000.0, parserName) + " ms.");
			Log.w("TEST_LEVEL6", String.format("%s Total Parse RUNTIME - " + totalParseTime, parserName) + " ms.");

		} // for

	}

	private void load_data(JSONInputs inputType) {
		try {
			int line_number = 1;
			String line = null;
			InputStream is = this.getAssets().open(jsonInputDataFiles.get(inputType.ordinal()));
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {

				String originalData = "";
				if (!jsonInputData.isEmpty()) {
					originalData += jsonInputData.get(inputType.ordinal());
				}

				jsonInputData.set(inputType.ordinal(), originalData + line);
				line_number++;
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int LocateKey(Object sourceArray, String key) {
		if (sourceArray instanceof JSONArray) {
			return LocateKey_JSON((JSONArray) sourceArray, key);
		} else if (sourceArray instanceof JsonArray) {
			return LocateKey_GSON((JsonArray) sourceArray, key);
		}
		return 0;
	}

	private int LocateKey_GSON(JsonArray sourceArray, String key) {
		int hitCount = 0;
		for (int i = 0; i < sourceArray.size(); i++) {
			JsonElement element = sourceArray.get(i);
			if (element.isJsonObject()) {
				JsonObject object = element.getAsJsonObject();
				if (object.has(key)) {
					hitCount++;
				}

				Set<Map.Entry<String, JsonElement>> memberSet = object.entrySet();
				Iterator it = memberSet.iterator();
				while (it.hasNext()) {
					Map.Entry tempVar = (Map.Entry) it.next();
					JsonElement possbileChildArray = (JsonElement) tempVar.getValue();
					if (possbileChildArray.isJsonArray()) {
						hitCount += LocateKey_GSON(possbileChildArray.getAsJsonArray(), key);
					}
				}
				return hitCount;
			} else {
				hitCount = LocateKey_GSON(element.getAsJsonArray(), key);
			}
		}
		return hitCount;
	}

	private int LocateKey_JSON(JSONArray sourceArray, String key) {
		int hitCount = 0;
		try {
			for (int i = 0; i < sourceArray.length(); i++) {
				Object obj = sourceArray.get(i);
				if (obj instanceof JSONObject) {
					JSONObject jsonObject = (JSONObject) obj;

					if (jsonObject.has(key)) {
						hitCount++;
					}
					// Identify all keys which have array values, and perform recrusive search
					Iterator keyIterator = jsonObject.keys();
					while (keyIterator.hasNext()) {
						Object value = jsonObject.get((String) keyIterator.next());
						if (value instanceof JSONArray) {
							hitCount += LocateKey_JSON((JSONArray) value, key);
						}
					}

					return hitCount;
				} else {
					hitCount = LocateKey_JSON((JSONArray) obj, key);
					return hitCount;
				}
			}
		} catch (JSONException e) {
			Log.w("Json Org Parser Exception", e.getMessage());
		}

		return hitCount;
	}

	/**
	 * 
	 * @return integer Array with 4 elements: user, system, idle and other cpu usage in percentage.
	 */
	private int[] getCpuUsageStatistic() {

		String tempString = executeTop();

		tempString = tempString.replaceAll(",", "");
		tempString = tempString.replaceAll("User", "");
		tempString = tempString.replaceAll("System", "");
		tempString = tempString.replaceAll("IOW", "");
		tempString = tempString.replaceAll("IRQ", "");
		tempString = tempString.replaceAll("%", "");
		for (int i = 0; i < 10; i++) {
			tempString = tempString.replaceAll("  ", " ");
		}
		tempString = tempString.trim();
		String[] myString = tempString.split(" ");
		int[] cpuUsageAsInt = new int[myString.length];
		for (int i = 0; i < myString.length; i++) {
			myString[i] = myString[i].trim();
			cpuUsageAsInt[i] = Integer.parseInt(myString[i]);
		}
		return cpuUsageAsInt;
	}

	private String executeTop() {
		java.lang.Process p = null;
		BufferedReader in = null;
		String returnString = null;
		try {
			p = Runtime.getRuntime().exec("top -n 1");
			in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while (returnString == null || returnString.contentEquals("")) {
				returnString = in.readLine();
			}
		} catch (IOException e) {
			Log.e("executeTop", "error in getting first line of top");
			e.printStackTrace();
		} finally {
			try {
				in.close();
				p.destroy();
			} catch (IOException e) {
				Log.e("executeTop", "error in closing and destroying top process");
				e.printStackTrace();
			}
		}
		return returnString;
	}
}
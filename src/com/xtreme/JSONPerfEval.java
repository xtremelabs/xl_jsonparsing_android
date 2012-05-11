package com.xtreme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.google.gson.JsonObject;

public class JSONPerfEval extends Activity {

	public static final int NUM_RUNS = 10;
	private static String QUERY_KEY = "field4_2";

	private enum JSONParsers {
		JSON_PARSER_JSON_ORG, JSON_PARSER_GSON, JSON_PARSER_GSON_READER, JSON_PARSER_JSON_SIMPLE, JSON_PARSER_JACKSON
	}

	private enum JSONInputs {
		JSON_INPUT_LEVEL6, JSON_INPUT_LEVEL10, JSON_INPUT_TWITTER, JSON_INPUT_FB
	}

	ArrayList<String> jsonInputData = null;
	ArrayList<String> jsonInputDataFiles = null;
	String outputResultText = "";

	public long TEST_JSON_ORG_TIME_NUMELEMS = 0L;
	public long TEST1_JSON_ORG_TIME = 0L;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout lLayout = (LinearLayout) inflater.inflate(R.layout.main, null);
		setContentView(lLayout);

		final Button button6 = (Button) findViewById(R.id.resLV6_button);
		final Button button10 = (Button) findViewById(R.id.resLV10_button);
		final Button buttonTwitter = (Button) findViewById(R.id.resTW_button);
		// final Button buttonFaceBook = (Button) findViewById(R.id.resFB_button);

		((EditText) findViewById(R.id.editText1)).setKeyListener(null);

		SetButtonHandlers(button6, JSONInputs.JSON_INPUT_LEVEL6);
		SetButtonHandlers(button10, JSONInputs.JSON_INPUT_LEVEL10);
		SetButtonHandlers(buttonTwitter, JSONInputs.JSON_INPUT_TWITTER);
		// SetButtonHandlers(buttonFaceBook, JSONInputs.JSON_INPUT_FB);

		// initialize and set data files
		jsonInputDataFiles = new ArrayList<String>(JSONInputs.values().length);
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_LEVEL6.ordinal(), "random6levels_small.txt");
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_LEVEL10.ordinal(), "random10levels_small.txt");
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_TWITTER.ordinal(), "twitter_100.txt");
		jsonInputDataFiles.add(JSONInputs.JSON_INPUT_FB.ordinal(), "fbwatermelon.txt");

		jsonInputData = new ArrayList<String>(JSONInputs.values().length);
		for (JSONInputs input : JSONInputs.values()) {
			jsonInputData.add(input.ordinal(), "");
		}
	}

	private class backgroundTask extends AsyncTask<Void, Void, Void> {

		EditText text = ((EditText) findViewById(R.id.editText1));
		JSONInputs input;

		public backgroundTask(JSONInputs inputType) {
			outputResultText = "";
			switch (inputType) {
			case JSON_INPUT_LEVEL6:
				QUERY_KEY = "field4_2";
				break;
			case JSON_INPUT_LEVEL10:
				QUERY_KEY = "field4_2";
				break;
			case JSON_INPUT_TWITTER:
				QUERY_KEY = "geo";
				break;
			case JSON_INPUT_FB:
				QUERY_KEY = "name";
				break;
			}
			input = inputType;

		}

		@Override
		protected void onPreExecute() {
			text.setText("Running tests...");
		}

		@Override
		protected Void doInBackground(Void... params) {
			RunJSONParsers(input);
			return null;
		}

		protected void onPostExecute(Void z) {
			text.setText(outputResultText);
		}

	}

	private void SetButtonHandlers(Button button, final JSONInputs inputType) {

		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				new backgroundTask(inputType).execute();
			}
		});
	}

	private void RunJSONParsers(JSONInputs inputType) {
		load_data(inputType);

		for (JSONParsers parser : JSONParsers.values()) {
			long start_time = 0L, start_JSONParse = 0L, end_JSONParse = 0L, end_JSONParseQuery = 0L;
			String parserName = "";
			long totalRunTime = 0L, totalParseTime = 0L;

			int hitCount = 0;

			for (int i = 0; i < NUM_RUNS; i++) {
				start_time = Debug.threadCpuTimeNanos();

				switch (parser) {
				case JSON_PARSER_JSON_ORG: {
					parserName = "JSON_ORG";

					try {
						JSONObject root = new JSONObject(jsonInputData.get(inputType.ordinal()));
						end_JSONParseQuery = Debug.threadCpuTimeNanos();
						Iterator keyIterator = root.keys();
						while (keyIterator.hasNext()) {
							Object value = root.get((String) keyIterator.next());
							if (value instanceof org.json.JSONArray) {
								start_JSONParse = Debug.threadCpuTimeNanos();
								hitCount = LocateKey_JSON((org.json.JSONArray) value, QUERY_KEY);
							}
						}
					} catch (Exception e) {
						Log.w("RunJSONOrgParsers", "Exception for parser JSON_ORG " + e.getMessage());
					}
					break;
				}

				case JSON_PARSER_GSON: {
					parserName = "GSON";

					com.google.gson.JsonParser jsonParser = new com.google.gson.JsonParser();
					JsonObject resultObject = jsonParser.parse(jsonInputData.get(inputType.ordinal())).getAsJsonObject();
					end_JSONParseQuery = Debug.threadCpuTimeNanos();

					start_JSONParse = Debug.threadCpuTimeNanos();
					Set<Map.Entry<String, com.google.gson.JsonElement>> memberSet = resultObject.entrySet();
					Iterator it = memberSet.iterator();
					while (it.hasNext()) {
						Map.Entry tempVar = (Map.Entry) it.next();
						com.google.gson.JsonElement possbileChildArray = (com.google.gson.JsonElement) tempVar.getValue();
						if (possbileChildArray.isJsonArray()) {
							hitCount = LocateKey_GSON(possbileChildArray.getAsJsonArray(), QUERY_KEY);
						}
					}

					break;
				}

				case JSON_PARSER_GSON_READER: {
					parserName = "GSON Reader";
					try {
						InputStream is = this.getAssets().open(jsonInputDataFiles.get(inputType.ordinal()));
						BufferedReader br = new BufferedReader(new InputStreamReader(is));
						com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(br);
						end_JSONParseQuery = Debug.threadCpuTimeNanos();
						start_JSONParse = Debug.threadCpuTimeNanos();
						hitCount = LocateKey_GSON_Reader(reader, QUERY_KEY);

					} catch (IOException e) {

					}

					break;
				}

				case JSON_PARSER_JSON_SIMPLE: {
					parserName = "JSON SIMPLE";
					org.json.simple.parser.JSONParser jsonParser = new org.json.simple.parser.JSONParser();
					Object object = new Object();
					try {
						object = jsonParser.parse(jsonInputData.get(inputType.ordinal()));
					} catch (Exception e) {
						Log.w("JSON Simple", "Unable to create parser based on the stream");
						break;
					}
					end_JSONParseQuery = Debug.threadCpuTimeNanos();

					start_JSONParse = Debug.threadCpuTimeNanos();
					org.json.simple.JSONObject resultObject = (org.json.simple.JSONObject) object;
					Iterator keyIterator = resultObject.keySet().iterator();
					while (keyIterator.hasNext()) {
						Object nextValue = resultObject.get(keyIterator.next());
						if (nextValue instanceof org.json.simple.JSONArray) {
							hitCount = LocateKey_JSON_Simple((org.json.simple.JSONArray) nextValue, QUERY_KEY);
						}
					}
					break;
				}

				case JSON_PARSER_JACKSON: {
					parserName = "JACKSON";
					JsonFactory jsonFactory = new JsonFactory();
					try {
						com.fasterxml.jackson.core.JsonParser jp = jsonFactory.createJsonParser(jsonInputData.get(inputType.ordinal()));
						end_JSONParseQuery = Debug.threadCpuTimeNanos();

						start_JSONParse = Debug.threadCpuTimeNanos();
						hitCount = LocateKey_JSON_Jackson(jp, QUERY_KEY);

					} catch (JsonParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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

			// Log.w("TEST_LEVEL6", String.format("%s Total RUNTIME - " + totalRunTime, parserName) + " ms.");
			// Log.w("TEST_LEVEL6", String.format("%s Initialization RUNTIME - " + (end_JSONParseQuery - start_time) / (long) 1000000.0, parserName) + " ms.");
			// Log.w("TEST_LEVEL6", String.format("%s Total Parse RUNTIME - " + totalParseTime, parserName) + " ms.");

			outputResultText += parserName + ":\n";
			outputResultText += String.format("Avg Runtime: %dms\n", totalRunTime);
			outputResultText += String.format("Avg Initialization Time: %dms\n", (end_JSONParseQuery - start_time) / (long) 1000000.0);
			outputResultText += String.format("Avg Seek Time: %dms\n", totalParseTime);
			outputResultText += String.format("HitCount : %d\n\n", hitCount);

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

	private int LocateKey_GSON_Reader(com.google.gson.stream.JsonReader reader, String key) {
		int hitCount = 0;
		try {
			if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
				reader.beginArray();
				while (reader.peek() != com.google.gson.stream.JsonToken.END_ARRAY) {
					hitCount += LocateKey_GSON_Reader_Helper(reader, key, 1);
				}
			} else if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
				reader.beginObject();
				hitCount += LocateKey_GSON_Reader_Helper(reader, key, 1);
			}
		} catch (IOException e) {

		}

		return hitCount;
	}

	private int LocateKey_GSON_Reader_Helper(com.google.gson.stream.JsonReader reader, String key, int level) {
		int hitCount = 0;
		try {
			while (reader.peek() != com.google.gson.stream.JsonToken.END_OBJECT) {

				if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
					reader.beginObject();
				}

				String keyName = reader.nextName();
				// Log.w("GSON Reader", "Key is " + keyName);

				if (keyName.equals(key)) {
					hitCount++;
				}

				if (reader.peek() == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
					reader.beginArray();
					while (reader.peek() != com.google.gson.stream.JsonToken.END_ARRAY) {
						hitCount += LocateKey_GSON_Reader_Helper(reader, key, level + 1);
					}
					reader.endArray();
				} else {
					reader.skipValue();
				}
			}

			reader.endObject();

		} catch (IOException e) {

		}

		return hitCount;
	}

	private int LocateKey_JSON_Jackson(com.fasterxml.jackson.core.JsonParser parser, String key) {
		int hitCount = 0;
		try {
			parser.nextToken();

			if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
				while (parser.nextToken() != JsonToken.END_ARRAY) {
					hitCount += LocateKey_JSON_Jackson_Helper(parser, key, 1);
				}
			} else if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
				hitCount += LocateKey_JSON_Jackson_Helper(parser, key, 1);
			}
		} catch (JsonParseException e) {
			Log.w("Jackson", "Bad Exception " + e.getMessage());
		} catch (IOException e) {
			Log.w("Jackson", "Bad Exception " + e.getMessage());
		}

		return hitCount;
	}

	private int LocateKey_JSON_Jackson_Helper(com.fasterxml.jackson.core.JsonParser parser, String key, int level) {
		int hitCount = 0;
		try {
			com.fasterxml.jackson.core.JsonToken jsonToken = parser.nextToken();
			while (jsonToken != JsonToken.END_OBJECT /* && jsonToken != JsonToken.END_ARRAY */) {
				if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
					String keyName = parser.getCurrentName();
					parser.nextToken();
					String txtValue = parser.getText();
					// Log.w("Jackson", String.format("Key: %s  Value:  %s", keyName, txtValue));

					if (keyName.equals(key)) {
						hitCount++;

					}

					if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
						com.fasterxml.jackson.core.JsonToken innerNextToken = parser.nextToken();

						while (innerNextToken != JsonToken.END_ARRAY) {
							if (innerNextToken == JsonToken.FIELD_NAME || innerNextToken == JsonToken.START_OBJECT) {
								hitCount += LocateKey_JSON_Jackson_Helper(parser, key, level + 1);
							}
							innerNextToken = parser.nextToken();
						}
					} else if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
						hitCount += LocateKey_JSON_Jackson_Helper(parser, key, level + 1);
					}

				} /*
				 * else { Log.w("Jackson", parser.getCurrentToken().toString()); }
				 */

				jsonToken = parser.nextToken();
			}
		} catch (JsonParseException e) {
			Log.w("Jackson", "Bad Exception " + e.getMessage());
		} catch (IOException e) {
			Log.w("Jackson", "Bad Exception " + e.getMessage());
		}

		// Log.w("Jackson", String.format("Returning from level %d with hitCount %d", level, hitCount));
		return hitCount;
	}

	private int LocateKey_JSON_Simple(org.json.simple.JSONArray sourceArray, String key) {
		int hitCount = 0;
		for (int i = 0; i < sourceArray.size(); i++) {
			Object object = sourceArray.get(i);
			if (object instanceof org.json.simple.JSONObject) {
				org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) sourceArray.get(i);
				if (jsonObject.containsKey(key)) {
					hitCount++;
				}

				Iterator keyIterator = jsonObject.keySet().iterator();
				while (keyIterator.hasNext()) {
					Object nextValue = jsonObject.get(keyIterator.next());
					if (nextValue instanceof org.json.simple.JSONArray) {
						hitCount += LocateKey_JSON_Simple((org.json.simple.JSONArray) nextValue, key);
					}
				}
			} else if (object instanceof org.json.simple.JSONArray) {
				hitCount = LocateKey_JSON_Simple((org.json.simple.JSONArray) sourceArray.get(i), key);
			}

		}
		return hitCount;
	}

	private int LocateKey_GSON(com.google.gson.JsonArray sourceArray, String key) {
		int hitCount = 0;
		for (int i = 0; i < sourceArray.size(); i++) {
			com.google.gson.JsonElement element = sourceArray.get(i);
			if (element.isJsonObject()) {
				com.google.gson.JsonObject object = element.getAsJsonObject();
				if (object.has(key)) {
					hitCount++;
				}

				Set<Map.Entry<String, com.google.gson.JsonElement>> memberSet = object.entrySet();
				Iterator it = memberSet.iterator();
				while (it.hasNext()) {
					Map.Entry tempVar = (Map.Entry) it.next();
					com.google.gson.JsonElement possbileChildArray = (com.google.gson.JsonElement) tempVar.getValue();
					if (possbileChildArray.isJsonArray()) {
						hitCount += LocateKey_GSON(possbileChildArray.getAsJsonArray(), key);
					}
				}
			} else {
				hitCount = LocateKey_GSON(element.getAsJsonArray(), key);
			}
		}
		return hitCount;
	}

	private int LocateKey_JSON(org.json.JSONArray sourceArray, String key) {
		int hitCount = 0;
		try {
			for (int i = 0; i < sourceArray.length(); i++) {
				Object obj = sourceArray.get(i);
				if (obj instanceof org.json.JSONObject) {
					org.json.JSONObject jsonObject = (org.json.JSONObject) obj;

					if (jsonObject.has(key)) {
						hitCount++;
					}
					// Identify all keys which have array values, and perform recursive search
					Iterator keyIterator = jsonObject.keys();
					while (keyIterator.hasNext()) {
						Object value = jsonObject.get((String) keyIterator.next());
						if (value instanceof org.json.JSONArray) {
							hitCount += LocateKey_JSON((org.json.JSONArray) value, key);
						}
					}

				} else {
					hitCount = LocateKey_JSON((org.json.JSONArray) obj, key);
				}
			}
		} catch (org.json.JSONException e) {
			Log.w("Json Org Parser Exception", e.getMessage());
		}

		return hitCount;
	}
}
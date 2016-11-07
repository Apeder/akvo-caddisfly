/*
 * Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Caddisfly
 *
 * Akvo Caddisfly is free software: you can redistribute it and modify it under the terms of
 * the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 * either version 3 of the License or any later version.
 *
 * Akvo Caddisfly is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License included below for more details.
 *
 * The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.caddisfly.sensor.colorimetry.strip.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.akvo.caddisfly.R;
import org.akvo.caddisfly.sensor.SensorConstants;
import org.akvo.caddisfly.sensor.colorimetry.strip.model.StripTest;
import org.akvo.caddisfly.sensor.colorimetry.strip.util.Constant;
import org.akvo.caddisfly.ui.BaseActivity;
import org.json.JSONException;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TestTypeListActivity extends BaseActivity {

    private StripAdapter adapter;
    private StripTest stripTest;
    private List<StripTest.Brand> brands;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_type_list);

        setTitle(R.string.selectTest);

        ListView listTypes = (ListView) findViewById(R.id.list_types);

        if (stripTest == null) {
            stripTest = new StripTest();
        }

        brands = stripTest.getBrandsAsList(this);

        if (brands != null) {
            //order alpha-numeric on brand
            Collections.sort(brands, new Comparator<StripTest.Brand>() {
                @Override
                public int compare(final StripTest.Brand object1, final StripTest.Brand object2) {
                    return object1.getName().compareToIgnoreCase(object2.getName());
                }
            });

            if (adapter == null) {
                adapter = new StripAdapter(this,
                        R.layout.item_test_type, brands);
            }
            listTypes.setAdapter(adapter);

            listTypes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    startDetailActivity(brands.get(i).getUuid());
                }
            });
        }
    }

    private void startDetailActivity(String uuid) {
        Intent detailIntent = new Intent(getBaseContext(), BrandInfoActivity.class);
        detailIntent.putExtra(Constant.UUID, uuid);
        detailIntent.putExtra("internal", getIntent().getBooleanExtra("internal", false));
        startActivityForResult(detailIntent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100) {
            Intent intent = new Intent(getIntent());
            setResult(resultCode, intent);

            if (resultCode == RESULT_OK) {
                intent.putExtra(SensorConstants.RESPONSE, data.getStringExtra(SensorConstants.RESPONSE));
                intent.putExtra("image", data.getStringExtra("image"));
            }

            // If an external activity is expecting the result then finish
            if (!getIntent().getBooleanExtra("internal", false)) {
                finish();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    static class StripAdapter extends ArrayAdapter<StripTest.Brand> {

        private final List<StripTest.Brand> brandList;
        private final int resource;
        private final Context context;
        private final StripTest stripTest;

        @SuppressWarnings("SameParameterValue")
        StripAdapter(Context context, int resource, List<StripTest.Brand> brandNames) {
            super(context, resource);

            this.context = context;
            this.resource = resource;
            this.brandList = brandNames;
            this.stripTest = new StripTest();
        }

        @Override
        public int getCount() {
            return brandList.size();
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            View view = convertView;
            ViewHolder holder;

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(resource, parent, false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            StripTest.Brand brand = brandList.get(position);

            if (stripTest != null) {

                if (brand != null) {

                    List<StripTest.Brand.Patch> patches = brand.getPatches();

                    if (patches != null && patches.size() > 0) {
                        StringBuilder ranges = new StringBuilder();
                        for (StripTest.Brand.Patch patch : patches) {
                            try {
                                int valueCount = patch.getColors().length();
                                if (ranges.length() > 0) {
                                    ranges.append(", ");
                                }
                                ranges.append(String.format(Locale.US, "%.0f - %.0f",
                                        patch.getColors().getJSONObject(0).getDouble("value"),
                                        patch.getColors().getJSONObject(valueCount - 1).getDouble("value")));

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            if (brand.getGroupingType() == StripTest.GroupType.GROUP) {
                                break;
                            }
                        }

                        holder.textView.setText(brand.getName());
                        holder.textSubtitle.setText(brand.getBrandDescription() + ", " + ranges.toString());
                    }
                }
            } else {
                holder.textView.setText(brand.getName());
            }
            return view;

        }

        private static class ViewHolder {

            private final TextView textView;
            private final TextView textSubtitle;

            ViewHolder(View v) {

                textView = (TextView) v.findViewById(R.id.text_title);
                textSubtitle = (TextView) v.findViewById(R.id.text_subtitle);
            }
        }
    }
}

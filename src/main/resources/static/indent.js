let currentEditCell = null;
let currentReasonCell = null;

let currentPage = 1;
let totalPages = 1;
let pageSize = 10;
let rawData = [];

$(document).ready(function () {
  fetchListingData();

  // Limit Selector
  $("#limitSelector").on("change", function () {
    pageSize = parseInt($(this).val());
    currentPage = 1;
    fetchListingData();
  });

  // Pagination
  $("#prevPage").on("click", function () {
    if (currentPage > 1) {
      currentPage--;
      fetchListingData();
    }
  });

  $("#nextPage").on("click", function () {
    if (currentPage < totalPages) {
      currentPage++;
      fetchListingData();
    }
  });

  // Search Button
  $("#searchBtn").on("click", function () {
    currentPage = 1;
    fetchListingData();
  });

  // Reset Filters
  $("#resetBtn").on("click", function () {
    $('#filterDate, #filterCategory, #filterPackFormat, #filterSection, #filterProduct').val('');
    currentPage = 1;
    totalPages = 1;
    pageSize = 10;
    fetchListingData();
  });

  // Filter change for differences
  $('#differenceFilter, #filterPlannedDiff, #filterPackedDiff, #filterDispatchedDiff, #filterReceivedDiff')
    .on('change', fetchListingData);
});

function fetchListingData() {
  const requestData = {
    reportDate: $('#filterDate').val(),
    category: $('#filterCategory').val(),
    product: $('#filterProduct').val(),
    section: $('#filterSection').val(),
    packFormat: $('#filterPackFormat').val(),
    page: currentPage,
    limit: pageSize,
    differenceFilter: {
      difference: $('#differenceFilter').val(),
      planned_diff: $('#filterPlannedDiff').val(),
      packed_diff: $('#filterPackedDiff').val(),
      dispatched_diff: $('#filterDispatchedDiff').val(),
      received_diff: $('#filterReceivedDiff').val()
    }
  };

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/search",
    method: "POST",
    contentType: "application/json",
    data: JSON.stringify(requestData),
    success: function (response) {
      rawData = response.indentdelivery || [];
      const tbody = $("#dataTable tbody").empty();

      rawData.forEach((row, index) => {
        const tr = $("<tr>");
        tr.append(`<td>${(currentPage - 1) * pageSize + index + 1}</td>`);
        tr.append(`<td>${row.reportDate || ""}</td>`);
        if (rawData.length > 0 && rawData[0].reportDate) {
          $('#filterDate').val(rawData[0].reportDate);
        }
        tr.append(`<td>${row.category || ""}</td>`);
        tr.append(`<td>${row.packFormat || ""}</td>`);
        tr.append(`<td>${row.section || ""}</td>`);
        tr.append(`<td>${row.code || ""}</td>`);
        tr.append(`<td>${row.product || ""}</td>`);

        tr.append(renderCell(row.indentQtyJson, "indentQty"));
        tr.append(renderCell(row.availableQtyJson, "availableQty"));
        tr.append(renderCell(row.requiredQtyJson, "requiredQty"));
        tr.append(`<td>${formatNumber(row.difference)}</td>`);
        tr.append(`<td class="reason-editable" data-field="reason">${row.reason || ""}</td>`);

        tr.append(renderCell(row.plannedQtyJson, "plannedQty"));
        tr.append(`<td>${formatNumber(row.plannedDifference)}</td>`);
        tr.append(`<td class="reason-editable" data-field="plannedReason">${row.plannedReason || ""}</td>`);

        tr.append(renderCell(row.packedQtyJson, "packedQty"));
        tr.append(`<td>${formatNumber(row.packedDifference)}</td>`);
        tr.append(`<td class="reason-editable" data-field="packedReason">${row.packedReason || ""}</td>`);

        tr.append(renderCell(row.dispatchedQtyJson, "dispatchedQty"));
        tr.append(`<td>${formatNumber(row.dispatchedDifference)}</td>`);
        tr.append(`<td class="reason-editable" data-field="dispatchedReason">${row.dispatchedReason || ""}</td>`);

        tr.append(renderCell(row.receivedQtyJson, "receivedQty"));
        tr.append(`<td>${formatNumber(row.receivedDifference)}</td>`);
        tr.append(`<td class="reason-editable" data-field="receivedReason">${row.receivedReason || ""}</td>`);

        tbody.append(tr);
      });

      setupAutocomplete();

      totalPages = response.totalPages || 1;
      $("#pageInfo").text(`Page ${currentPage} of ${totalPages}`);

      fetchSummaryCards(rawData[0]?.reportDate);
    },
    error: function (err) {
      console.error("❌ Fetch Error:", err);
      Swal.fire({
        icon: "error",
        title: "Fetch Failed",
        text: "Could not load listing data."
      });
    }
  });
}

function renderCell(jsonData, field) {
  let total = 0;
  let values = [];

  if (jsonData) {
    try {
      const parsed = JSON.parse(jsonData);
      if (Array.isArray(parsed.data)) {
        values = parsed.data.map(Number).filter(n => !isNaN(n));
      } else if (!isNaN(parsed.data)) {
        values = [Number(parsed.data)];
      }
      total = values.reduce((sum, v) => sum + v, 0);
    } catch (e) {
      console.warn("Invalid JSON:", jsonData, e);
    }
  }

  // ✅ Just return total — no breakdown shown in small text
  return `<td class="editable" data-field="${field}" data-values='${JSON.stringify(values)}'>
            ${formatNumber(total)}
          </td>`;
}


function formatNumber(value) {
  const number = parseFloat(value);
  return isNaN(number) ? "0" : new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(number);
}
// === QUANTITY MODAL HANDLING ===
function updateTotal() {
  let total = 0;
  $(".qtyInput").each(function () {
    const val = parseFloat($(this).val());
    if (!isNaN(val)) total += val;
  });
  $("#totalLabel").text(`Total: ${total.toFixed(2)}`);
}

// On click editable quantity cell
$("#dataTable").on("click", "td.editable", function () {
  currentEditCell = $(this);
  const field = currentEditCell.data("field");

  const fieldLabels = {
    indentQty: "Indent Qty",
    availableQty: "Available Qty",
    requiredQty: "Required Qty",
    plannedQty: "Planned Qty",
    packedQty: "Packed Qty",
    dispatchedQty: "Dispatched Qty",
    receivedQty: "Received Qty"
  };

  $("#fieldLabel").text(fieldLabels[field] || field);

  let values = currentEditCell.data("values");
  if (!values) {
    const rawText = currentEditCell.text().trim().split("(")[1]?.replace(")", "") || "";
    values = rawText.split("+").map(v => parseFloat(v.trim())).filter(v => !isNaN(v));
  }

  if (!Array.isArray(values)) values = [];

  $("#editFieldContainer").empty();
  values.forEach(val => {
    $("#editFieldContainer").append(`
      <div class="input-group mb-2 value-row">
        <input type="number" class="form-control qtyInput" value="${val}" />
        <button class="btn btn-danger removeVal" type="button">&times;</button>
      </div>
    `);
  });

  updateTotal();
  $("#rowModal").modal("show");
});

// Add new value row
$("#cloneRow").on("click", function () {
  $("#editFieldContainer").append(`
    <div class="input-group mb-2 value-row">
      <input type="number" class="form-control qtyInput" />
      <button class="btn btn-danger removeVal" type="button">&times;</button>
    </div>
  `);
  updateTotal();
});

// Remove value row
$("#editFieldContainer").on("click", ".removeVal", function () {
  $(this).closest(".value-row").remove();
  updateTotal();
});

// Update total on input change
$("#editFieldContainer").on("input", ".qtyInput", updateTotal);

// Save edited quantity
$("#saveRowData").on("click", function () {
  const values = $(".qtyInput").map(function () {
    return parseFloat($(this).val()) || 0;
  }).get().filter(v => v !== 0);

  const total = values.reduce((a, b) => a + b, 0);

  currentEditCell.data("values", values);
  currentEditCell.html(`
    ${total.toFixed(2)}
    <br><small class="text-muted">(${values.join(" + ")})</small>
  `);

  const row = currentEditCell.closest("tr");
  const code = row.find("td:nth-child(6)").text().trim();
  const reportDate = row.find("td:nth-child(2)").text().trim();
  const field = currentEditCell.data("field");

  const payload = {
    code: code,
    field: field,
    value: JSON.stringify({ data: values }),
    date: reportDate
  };

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/updatefieldbycode",
    method: "POST",
    contentType: "application/json",
    data: JSON.stringify(payload),
    success: function (response) {
      Swal.fire({
        icon: "success",
        title: "Updated Successfully ✅",
        text: response || "Data updated successfully."
      });

      $("#rowModal").modal("hide");
      fetchListingData();
    },
    error: function (err) {
      console.error("❌ Failed to update quantity:", err);
      Swal.fire({
        icon: "error",
        title: "Update Failed ❌",
        text: "Could not save the changes. Please try again."
      });
    }
  });
});

// === REASON MODAL HANDLING ===

$(document).on("click", ".reason-editable", function () {
  currentReasonCell = $(this);
  const currentReason = currentReasonCell.text().trim();
  $("#reasonInput").val(currentReason);
  $("#reasonModal").modal("show");
});

$("#saveReason").on("click", function (e) {
  e.preventDefault();

  const newReason = $("#reasonInput").val().trim();
  if (!currentReasonCell) return;

  const row = currentReasonCell.closest("tr");
  const code = row.find("td:nth-child(6)").text().trim();
  const reportDate = row.find("td:nth-child(2)").text().trim();
  const colIndex = currentReasonCell.index();

  // const reasonFields = {
  //   10: "reason",
  //   13: "plannedReason",
  //   16: "packedReason",
  //   19: "dispatchedReason",
  //   22: "receivedReason"
  // };


  const field = currentReasonCell.data("field");
  if (!field) return;

  const payload = {
    code: code,
    field: field,
    value: newReason,
    date: reportDate
  };

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/updatereason",
    method: "POST",
    contentType: "application/json",
    data: JSON.stringify(payload),
    success: function () {
      currentReasonCell.text(newReason);
      Swal.fire({
        icon: "success",
        title: "Reason Updated ✅",
        text: "Reason saved successfully."
      });
      $("#reasonModal").modal("hide");
      fetchListingData();
    },
    error: function (err) {
      console.error("❌ Failed to update reason:", err);
      Swal.fire({
        icon: "error",
        title: "Update Failed ❌",
        text: "Could not update reason. Try again."
      });
    }
  });
});

// === SUMMARY CARDS ===
function fetchSummaryCards(reportDate) {
  if (!reportDate) {
    $("#summaryCards").html("<div class='text-muted'>No report date to show summary.</div>");
    return;
  }

  const requestData = { reportdate: reportDate };

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/summary",
    method: "POST",
    contentType: "application/json",
    data: JSON.stringify(requestData),
    success: function (response) {
      const overall = response.summary || {};
      const page = response.pageSummary || {};
      const totalProducts = page.totalProducts || 0;

      const card = (label, value, footnote = '', variant = '') => `
        <div class="card ${variant}">
          <div class="card-title">${label}</div>
          <div class="card-value">${formatNumber(value)}</div>
          ${footnote ? `<div class="card-subtext">${footnote}</div>` : ''}
        </div>
      `;

      const summaryHTML = `
        ${card("Total Products", totalProducts, "", "primary")}
        ${card("Total Indent", page.totalIndent, `${page.productWithIndent}/${totalProducts}`, "info")}
        ${card("Total Available", page.totalAvailable, `${page.productWithAvailable}/${totalProducts}`, "success")}
        ${card("Total Required", page.totalRequired, `${page.productWithRequired}/${totalProducts}`, "danger")}
        ${card("Total Planned", page.totalPlanned, `${page.productWithPlanned}/${totalProducts}`, "warning")}
        ${card("Total Packed", page.totalPacked, `${page.productWithPacked}/${totalProducts}`, "primary")}
        ${card("Total Dispatched", page.totalDispatched, `${page.productWithDispatched}/${totalProducts}`, "info")}
        ${card("Total Received", page.totalReceived, `${page.productWithReceived}/${totalProducts}`, "success")}
      `;

      $("#summaryCards").html(`<div class="cards">${summaryHTML}</div>`);
    },
    error: function (err) {
      console.error("❌ Failed to fetch summary", err);
      $("#summaryCards").html("<div class='text-danger'>Failed to load summary</div>");
    }
  });
}

// === AUTOCOMPLETE FOR FILTERS ===
function setupAutocomplete() {
  const extractUnique = (field) => [...new Set(rawData.map(r => r[field]).filter(Boolean))];

  const fields = [
    { id: "filterCategory", key: "category" },
    { id: "filterPackFormat", key: "packFormat" },
    { id: "filterSection", key: "section" },
    { id: "filterProduct", key: "product" }
  ];

  fields.forEach(({ id, key }) => {
    const suggestions = extractUnique(key);
    const $input = $(`#${id}`);
    if ($input.length && suggestions.length) {
      if ($input.data("ui-autocomplete")) {
        $input.autocomplete("destroy");
      }

      $input.autocomplete({
        source: suggestions,
        minLength: 0
      }).focus(function () {
        $(this).autocomplete("search", this.value);
      });
    }
  });
}

// === INDENT FILE UPLOAD ===
$("#indentUploadForm").on("submit", function (e) {
  e.preventDefault();
  const file = $("#indentFile")[0].files[0];
  if (!file) {
    return Swal.fire("Select File", "Please choose a file to upload.", "warning");
  }

  const formData = new FormData();
  formData.append("file", file);

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/uploadindentvsdelivery",
    method: "POST",
    processData: false,
    contentType: false,
    data: formData,
    success: function () {
      $("#indentModal").modal("hide");
      Swal.fire("Success ✅", "Indent file uploaded.", "success");
      fetchListingData();
    },
    error: function (err) {
      console.error("❌ Indent upload failed:", err);
      Swal.fire("Error ❌", "Failed to upload Indent file.", "error");
    }
  });
});

// === QUANTITY FILE UPLOAD ===
$("#qtyUploadForm").on("submit", function (e) {
  e.preventDefault();

  const file = $("#qtyFile")[0].files[0];
  const quantityType = $("#quantityType").val();

  if (!file || !quantityType) {
    return Swal.fire("Missing Input", "Please select both file and quantity type.", "warning");
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("type", quantityType);

  $.ajax({
    url: "https://spring-app-sks.onrender.com/indentvsdelivery/uploadqty",
    method: "POST",
    data: formData,
    processData: false,
    contentType: false,
    xhrFields: { responseType: 'blob' },
    success: function (data, status, xhr) {
      const blob = new Blob([data], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      });

      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;

      const contentDisposition = xhr.getResponseHeader("Content-Disposition");
      const fileName = contentDisposition
        ? contentDisposition.split("filename=")[1].replace(/"/g, "")
        : "upload_status.xlsx";

      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);

      Swal.fire("Uploaded ✅", "Check the downloaded status file.", "success");
      fetchListingData();
      $("#qtyUploadModal").modal("hide");
    },
    error: function (err) {
      console.error("❌ Upload error:", err);
      Swal.fire("Upload Failed ❌", "Something went wrong. Please try again.", "error");
    }
  });
});

// === DOWNLOAD TEMPLATE (QUANTITY) ===
function downloadTemplate() {
  const type = document.getElementById("quantityType").value;
  if (!type) {
    Swal.fire("Select Type", "Please choose a Quantity Type before downloading.", "warning");
    return;
  }

  const formattedType = encodeURIComponent(type);
  const url = `https://spring-app-sks.onrender.com/indentvsdelivery/qtytemplate?type=${formattedType}`;
  window.open(url, "_blank");
}

// === DOWNLOAD TEMPLATE (OVERALL) ===
function downloadOverallTemplate() {
  const a = document.createElement("a");
  a.href = "https://spring-app-sks.onrender.com/indentvsdelivery/overalltemplate";
  a.download = "overall_indent_template.xlsx";
  a.click();
}

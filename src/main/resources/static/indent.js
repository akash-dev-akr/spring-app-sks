let currentEditCell = null;
let currentReasonCell = null;

let currentPage = 1;
let totalPages = 1;
let pageSize = 10; // Default limit

$(document).ready(function () {

  fetchListingData();

  // Limit selector change
  $("#limitSelector").on("change", function () {
    pageSize = parseInt($(this).val());
    currentPage = 1;
    fetchListingData();
  });

  // Pagination controls
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

  // Search button
  $("#searchBtn").on("click", function () {
    currentPage = 1;
    fetchListingData();
  });
  let currentEditCell = null;

  // Utility: update total from inputs
  function updateTotal() {
    let total = 0;
    $(".qtyInput").each(function () {
      const val = parseFloat($(this).val());
      if (!isNaN(val)) total += val;
    });
    $("#totalLabel").text(`Total: ${total}`);
  }

  // === QUANTITY MODAL ===
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

    $("#fieldLabel").text(fieldLabels[field] || field); // label fix

    let values = currentEditCell.data("values");
    if (!values) {
      const rawText = currentEditCell.text().trim().split("(")[1]?.replace(")", "") || "";
      values = rawText.split("+").map(v => parseFloat(v.trim())).filter(v => !isNaN(v));
    }

    $("#editFieldContainer").empty();
    if (!Array.isArray(values)) values = [];

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

  // Add value row
  $("#cloneRow").on("click", function () {
    $("#editFieldContainer").append(`
    <div class="input-group mb-2 value-row">
      <input type="number" class="form-control qtyInput" value="" />
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

  // Update total on input
  $("#editFieldContainer").on("input", ".qtyInput", updateTotal);

  // Save changes
  $("#saveRowData").on("click", function () {
    const values = $(".qtyInput").map(function () {
      return parseFloat($(this).val()) || 0;
    }).get().filter(v => v !== 0);

    const total = values.reduce((a, b) => a + b, 0);

    // Update cell display
    currentEditCell.data("values", values);
    currentEditCell.html(`
    ${total.toFixed(2)}
    <br><small class="text-muted">(${values.join(" + ")})</small>
  `);

    // Prepare payload for API
    const row = currentEditCell.closest("tr");
    const code = row.find("td:nth-child(5)").text().trim();         // update if code is in a different column
    const reportDate = row.find("td:nth-child(1)").text().trim();   // update if date is in another column
    const field = currentEditCell.data("field");

    const payload = {
      code: code,
      field: field, // FIXED: no "Json" suffix
      value: JSON.stringify({ data: values }),
      date: reportDate
    };

    // Send update to backend
    $.ajax({
      url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/updatefieldbycode",
      method: "POST",
      contentType: "application/json",
      data: JSON.stringify(payload),
      success: function (response) {
        console.log("✅ Received response:", response);
        Swal.fire({
          title: "Updated!",
          text: response,
          icon: "success",
          confirmButtonText: "OK"
        });
        fetchListingData();
      },

      error: function (err) {
        console.error("Failed to update quantity:", err);
      }
    });

    $("#rowModal").modal("hide");
  });

  // === REASON MODAL ===
  $("#dataTable").on("click", "td.reason-editable", function () {
    currentReasonCell = $(this);
    const existingReason = currentReasonCell.text().trim();
    $("#reasonInput").val(existingReason);
    $("#reasonModal").modal("show");
  });

  $("#saveReason").on("click", function () {
    const newReason = $("#reasonInput").val().trim();
    if (!currentReasonCell) return;

    currentReasonCell.text(newReason);

    const row = currentReasonCell.closest("tr");
    const code = row.find("td:nth-child(5)").text().trim();
    const colIndex = currentReasonCell.index();
    const reportDate = row.find("td:nth-child(1)").text().trim();
    const reasonFields = {
      10: "reason",
      13: "plannedReason",
      16: "packedReason",
      19: "dispatchedReason",
      22: "receivedReason"
    };
    const field = reasonFields[colIndex];

    if (!field) return;

    const payload = {
      code: code,
      field: field,
      value: newReason,
      date: reportDate
    };

    $.ajax({
      url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/updatereason",
      method: "POST",
      contentType: "application/json",
      data: JSON.stringify(payload),
      success: function () {
        console.log("Reason updated:", field);
      },
      error: function (err) {
        console.error("Failed to update reason:", err);
      }
    });

    $("#reasonModal").modal("hide");
  });
});
$('#resetBtn').on('click', function () {
  $('#filterDate').val('');
  $('#filterCategory').val('');
  $('#filterPackFormat').val('');
  $('#filterSection').val('');
  $('#filterProduct').val('');
 currentPage = 1;
 totalPages = 1;
 pageSize = 10; 
  fetchListingData();
});

function updateTotal() {
  const total = $(".qtyInput").toArray().reduce((sum, el) => {
    return sum + (parseFloat($(el).val()) || 0);
  }, 0);
  $("#totalLabel").text(`Total: ${total.toFixed(2)}`);
}
$('#differenceFilter, #filterPlannedDiff, #filterPackedDiff, #filterDispatchedDiff, #filterReceivedDiff')
  .on('change', fetchListingData);

let rawData = [];
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
    url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/search",
    method: "POST",
    contentType: "application/json",
    data: JSON.stringify(requestData),
    success: function (response) {
      rawData = response.indentdelivery || [];
      const tbody = $("#dataTable tbody").empty();

      rawData.forEach((row, index) => {
        const tr = $("<tr>");
        tr.append(`<td>${(currentPage - 1) * pageSize + index + 1}</td>`); // S.No
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
        tr.append(`<td class="reason-editable">${row.reason || ""}</td>`);

        tr.append(renderCell(row.plannedQtyJson, "plannedQty"));
        tr.append(`<td>${formatNumber(row.plannedDifference)}</td>`);
        tr.append(`<td class="reason-editable">${row.plannedReason || ""}</td>`);

        tr.append(renderCell(row.packedQtyJson, "packedQty"));
        tr.append(`<td>${formatNumber(row.packedDifference)}</td>`);
        tr.append(`<td class="reason-editable">${row.packedReason || ""}</td>`);

        tr.append(renderCell(row.dispatchedQtyJson, "dispatchedQty"));
        tr.append(`<td>${formatNumber(row.dispatchedDifference)}</td>`);
        tr.append(`<td class="reason-editable">${row.dispatchedReason || ""}</td>`);

        tr.append(renderCell(row.receivedQtyJson, "receivedQty"));
        tr.append(`<td>${formatNumber(row.receivedDifference)}</td>`);
        tr.append(`<td class="reason-editable">${row.receivedReason || ""}</td>`);

        tbody.append(tr);
      });

      setupAutocomplete();

      // Pagination
      totalPages = response.totalPages || 1;
      $("#pageInfo").text(`Page ${currentPage} of ${totalPages}`);

      // Call summary API separately
      fetchSummaryCards(rawData[0].reportDate);
    },
    error: function (err) {
      alert("Failed to fetch data");
      console.error(err);
    }
  });

}function fetchSummaryCards(reportDate) {
  const requestData = {
    reportdate: reportDate
  };

  $.ajax({
    url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/summary",
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
      console.error("Failed to fetch summary", err);
      $("#summaryCards").html("<div class='text-danger'>Failed to load summary</div>");
    }
  });
}


// Format number (no decimal) with commas
function formatNumber(value) {
  const number = parseFloat(value);
  return isNaN(number) ? "0" : new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 }).format(number);
}

// Setup autocomplete for filters
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
      // ✅ Only destroy if initialized
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


// Render cell with parsed JSON and total
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

  const html = `${formatNumber(total)}`;
  return `<td class="editable" data-field="${field}" data-values='${JSON.stringify(values)}'>${html}</td>`;
}




// === INDENT FILE UPLOAD ===
$("#indentUploadForm").on("submit", function (e) {
  e.preventDefault();
  const file = $("#indentFile")[0].files[0];
  if (!file) return alert("Please select a file.");

  const formData = new FormData();
  formData.append("file", file);

  $.ajax({
    url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/uploadindentvsdelivery", // Your backend API
    method: "POST",
    processData: false,
    contentType: false,
    data: formData,
    success: function () {

      $("#indentModal").modal("hide");
      fetchListingData();
    },
    error: function (err) {
      console.error("❌ Indent upload failed:", err);
      alert("Failed to upload Indent file.");
    }
  });
});


// === QUANTITY FILE UPLOAD ===
$("#qtyUploadForm").on("submit", function (e) {
  e.preventDefault();

  const file = $("#qtyFile")[0].files[0];
  const quantityType = $("#quantityType").val();

  if (!file || !quantityType) {
    return Swal.fire({
      icon: 'warning',
      title: 'Missing Input',
      text: 'Please select both file and quantity type.'
    });
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("type", quantityType);

  $.ajax({
    url: "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/uploadqty",
    method: "POST",
    data: formData,
    processData: false,
    contentType: false,
    xhrFields: {
      responseType: 'blob' // Ensures file is treated as binary
    },
    success: function (data, status, xhr) {
      const blob = new Blob([data], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      });

      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;

      // Extract filename from headers
      const contentDisposition = xhr.getResponseHeader("Content-Disposition");
      const fileName = contentDisposition
        ? contentDisposition.split("filename=")[1].replace(/"/g, "")
        : "upload_status.xlsx";

      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);

      Swal.fire({
        icon: "success",
        title: "Uploaded Successfully ✅",
        text: "Check the downloaded file for status details."
      });
      fetchListingData();

      $("#qtyUploadModal").modal("hide");
    },
    error: function (err) {
      console.error("Upload error:", err);
      Swal.fire({
        icon: "error",
        title: "Upload Failed ❌",
        text: "Something went wrong. Please try again."
      });
    }
  });
});


// === DOWNLOAD QUANTITY TEMPLATE ===
function downloadTemplate() {
  const type = document.getElementById("quantityType").value;
  if (!type) {
    alert("Please select a Quantity Type before downloading template.");
    return;
  }

  const formattedType = encodeURIComponent(type);
  const url = `https://my-spring-app-ck1f.onrender.com/indentvsdelivery/qtytemplate?type=${formattedType}`;
  window.open(url, "_blank");
}

document.addEventListener("DOMContentLoaded", () => {
  const indentForm = document.getElementById("indentUploadForm");
  const fileInput = document.getElementById("indentFile");

  indentForm.addEventListener("submit", async function (e) {
    e.preventDefault();

    if (!fileInput.files.length) {
      return Swal.fire({
        icon: "warning",
        title: "No File Selected",
        text: "Please select an Excel file before uploading.",
      });
    }

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);

    try {
      const response = await fetch("https://my-spring-app-ck1f.onrender.com/indentvsdelivery/uploadindentvsdelivery", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) throw new Error("Upload failed");

      const blob = await response.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = downloadUrl;
      a.download = "upload_status.xlsx";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(downloadUrl);
      document.body.removeChild(a);

      await Swal.fire({
        icon: "success",
        title: "Uploaded Successfully ✅",
        text: "Status file has been downloaded.",
      });

      const modalEl = document.getElementById("indentModal");
      const modalInstance = bootstrap.Modal.getInstance(modalEl);
      if (modalInstance) modalInstance.hide();
      indentForm.reset();

      if (typeof fetchListingData === "function") fetchListingData();

    } catch (err) {
      console.error("Upload error:", err);
      Swal.fire({
        icon: "error",
        title: "Upload Failed ❌",
        text: "An error occurred while uploading. Please try again.",
      });
    }
  });
});


// ✅ Template Download
function downloadOverallTemplate() {
  const a = document.createElement("a");
  a.href = "https://my-spring-app-ck1f.onrender.com/indentvsdelivery/overalltemplate";
  a.download = "overall_indent_template.xlsx";
  a.click();
}

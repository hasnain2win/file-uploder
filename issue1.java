private Set<CAGProfileBulkUploadDTO> validateXlsxFile(MultipartFile file) throws IOException {
        Set<CAGProfileBulkUploadDTO> cagProfileBulkUploadDTOs = new HashSet<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String[] data = new String[row.getPhysicalNumberOfCells()];
                for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
                    if (row.getCell(j) == null) {
                        data[j] = "";
                    } else {
                        data[j] = row.getCell(j).toString().trim();
                    }
                }
                CAGProfileBulkUploadDTO cagProfileBulkUpload = createCAGProfileBulkUploadDTO(data);
                validateCAGProfileBulkUploadDTO(cagProfileBulkUpload);
                cagProfileBulkUploadDTOs.add(cagProfileBulkUpload);
            }
        }
        return cagProfileBulkUploadDTOs;
    }

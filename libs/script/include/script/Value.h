#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace pcp::script {

class Value {
public:
    enum class Kind {
        Number,
        Bool,
        String,
        Image,
        Array
    };

    static Value number(double value);
    static Value boolean(bool value);
    static Value string(std::string value);
    static Value image(std::string name, std::string assetPath);
    static Value array(std::vector<Value> items);

    Kind kind() const { return kind_; }
    double number() const { return number_; }
    bool boolean() const { return boolean_; }
    const std::string& string() const { return text_; }
    const std::string& imageName() const { return imageName_; }
    const std::string& assetPath() const { return assetPath_; }
    const std::vector<Value>& items() const { return *items_; }

private:
    Kind kind_ = Kind::Bool;
    double number_ = 0.0;
    bool boolean_ = false;
    std::string text_;
    std::string imageName_;
    std::string assetPath_;
    std::shared_ptr<const std::vector<Value>> items_;
};

Value imageValue(std::string_view objectName);
bool truthy(const Value& value);
std::string displayText(const Value& value);
std::string debugText(const Value& value);
bool operator==(const Value& left, const Value& right);
bool operator!=(const Value& left, const Value& right);

}  // namespace pcp::script
